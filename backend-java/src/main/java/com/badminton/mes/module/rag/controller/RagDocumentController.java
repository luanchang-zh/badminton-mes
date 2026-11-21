package com.badminton.mes.module.rag.controller;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import com.badminton.mes.common.core.CommonResult;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.RequiresRoles;
import com.badminton.mes.common.security.RoleCodeConstants;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.controller.vo.RagDocumentRespVO;
import com.badminton.mes.module.rag.controller.vo.RagTextUploadReqVO;
import com.badminton.mes.module.rag.service.RagDocumentService;
import com.badminton.mes.module.rag.client.dto.RagDocumentContent;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 知识库文档 Controller。
 *
 * <p>Web 层保持单薄：校验文件名和文本编码，原件由服务层转发。
 * <b>所有接口都不接收租户参数</b>，租户由 Service 内部按登录用户解析，
 * 这是越权防护的根本手段。
 *
 * <p>登录保护由 {@code AuthInterceptor} 对 {@code /api/**} 默认生效，无需额外注解；
 * 文档写操作（上传、删除、重新处理）限管理员与工艺工程师，
 * 查询接口登录即可，避免普通操作工误删共享知识库内容。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@RestController
@RequestMapping("/api/rag/documents")
public class RagDocumentController {

    /** 允许上传的扩展名白名单 */
    private static final List<String> TEXT_FILE_EXTENSIONS =
            List.of(".txt", ".md", ".markdown", ".csv", ".json", ".log");
    private static final List<String> BINARY_FILE_EXTENSIONS = List.of(".pdf", ".docx", ".xlsx", ".pptx");

    private final RagDocumentService ragDocumentService;

    /**
     * 构造器注入，依赖不可变。
     *
     * @param ragDocumentService 文档 Service
     */
    public RagDocumentController(RagDocumentService ragDocumentService) {
        this.ragDocumentService = ragDocumentService;
    }

    /**
     * 上传文件作为知识库文档。
     *
     * <p>文本先校验编码，文件统一以原始字节交给租户解析器。
     *
     * @param file 上传文件
     * @return 上游返回的任务追踪号，供前端查询处理进度
     */
    @PostMapping
    @RequiresRoles({RoleCodeConstants.ADMIN, RoleCodeConstants.CRAFT_ENGINEER})
    public CommonResult<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        String fileName = requireSupportedFileName(file);
        return CommonResult.success(ragDocumentService.uploadFile(fileName, file));
    }

    /**
     * 以纯文本形式上传文档。
     *
     * <p>为小程序或脚本调用准备：它们往往只有文本，没有文件对象。
     *
     * @param reqVO 文本上传请求
     * @return 上游返回的任务追踪号
     */
    @PostMapping("/text")
    @RequiresRoles({RoleCodeConstants.ADMIN, RoleCodeConstants.CRAFT_ENGINEER})
    public CommonResult<String> uploadText(@Valid @RequestBody RagTextUploadReqVO reqVO) {
        return CommonResult.success(
                ragDocumentService.uploadText(reqVO.fileSource(), reqVO.content()));
    }

    /**
     * 列出当前租户的文档。
     *
     * @return 文档列表，含归一化处理状态
     */
    @GetMapping
    public CommonResult<List<RagDocumentRespVO>> listDocuments() {
        // 列表接口无数据时返回空数组而非 null(API-002)，由 VO 的批量转换保证
        return CommonResult.success(
                RagDocumentRespVO.fromAll(ragDocumentService.listDocuments()));
    }

    /** 预览当前租户文档的提取文本。 */
    @GetMapping("/{docId}/text")
    public CommonResult<RagDocumentContent> readDocument(@PathVariable String docId) {
        return CommonResult.success(ragDocumentService.readDocument(docId));
    }

    /** 返回当前租户的上传原件，禁止浏览器按内容猜测类型。 */
    @GetMapping("/{docId}/content")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String docId) {
        var document = ragDocumentService.downloadDocument(docId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(document.content());
    }

    /** 新版独立处理，失败时仍可访问原有版本。 */
    @PostMapping("/{docId}/versions")
    @RequiresRoles({RoleCodeConstants.ADMIN, RoleCodeConstants.CRAFT_ENGINEER})
    public CommonResult<String> uploadVersion(@PathVariable String docId,
            @RequestParam("file") MultipartFile file) {
        String fileName = requireSupportedFileName(file);
        return CommonResult.success(ragDocumentService.uploadFileVersion(docId, fileName, file));
    }

    /**
     * 删除当前租户下的文档。
     *
     * <p>{@code docId} 只在当前租户的路由前缀下生效：即使拿到其他租户的标识，
     * 上游也查不到对应文档，因此不需要在这里额外比对归属。
     *
     * @param docId 文档标识
     * @return 空数据成功响应
     */
    @DeleteMapping("/{docId}")
    @RequiresRoles({RoleCodeConstants.ADMIN, RoleCodeConstants.CRAFT_ENGINEER})
    public CommonResult<Void> deleteDocument(
            @PathVariable("docId") @NotBlank(message = "文档标识不能为空") String docId) {
        ragDocumentService.deleteDocument(docId);
        return CommonResult.success(null);
    }

    /**
     * 重新处理当前租户下所有失败、待处理或中断的文档。
     *
     * <p><b>不是「重建单个文档」</b>：上游该接口不接受文档级过滤，一次调用会把
     * 整个租户的失败文档全部重跑。若只想重建某一个文档，请先删除再重新上传。
     *
     * @return 空数据成功响应
     */
    @PostMapping("/reprocess-failed")
    @RequiresRoles({RoleCodeConstants.ADMIN, RoleCodeConstants.CRAFT_ENGINEER})
    public CommonResult<Void> reprocessFailed() {
        ragDocumentService.reprocessFailedDocuments();
        return CommonResult.success(null);
    }

    /**
     * 校验文件名并返回它。
     *
     * @param file 上传文件
     * @return 原始文件名
     * @throws ServiceException 文件为空、无文件名或扩展名不在白名单
     */
    private String requireSupportedFileName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_READ_FAILED);
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank() || fileName.length() > 200
                || fileName.contains("/") || fileName.contains("\\")
                || fileName.contains(".[") || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_READ_FAILED);
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        boolean text = TEXT_FILE_EXTENSIONS.stream().anyMatch(lowerCaseName::endsWith);
        if (!text && BINARY_FILE_EXTENSIONS.stream().noneMatch(lowerCaseName::endsWith)) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_TYPE_UNSUPPORTED);
        }
        if (text) {
            requireUtf8(file);
        }
        return fileName;
    }

    /**
     * 校验文本编码，后续仍提交原始字节以保留换行和转义字符。
     *
     * @param file 上传文件
     * @throws ServiceException 读取失败或编码不是 UTF-8
     */
    private void requireUtf8(MultipartFile file) {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8.newDecoder())) {
            // 用固定缓冲校验，避免在转发前创建一份完整文本副本。
            reader.transferTo(java.io.Writer.nullWriter());
        } catch (IOException readError) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_READ_FAILED);
        }
    }
}
