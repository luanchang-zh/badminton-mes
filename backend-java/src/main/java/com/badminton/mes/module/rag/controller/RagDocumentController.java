package com.badminton.mes.module.rag.controller;

import java.io.IOException;
import java.nio.ByteBuffer;
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
 * <p>Web 层保持单薄：参数校验、文件转文本、转发 Service。
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
     * <p>只接受能按 UTF-8 解析的文本类文件：二进制格式需要上游安装对应的解析器，
     * 直接拒收比让上游异步失败更早也更清晰。
     *
     * @param file 上传文件
     * @return 上游返回的任务追踪号，供前端查询处理进度
     */
    @PostMapping
    @RequiresRoles({RoleCodeConstants.ADMIN, RoleCodeConstants.CRAFT_ENGINEER})
    public CommonResult<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        String fileName = requireSupportedFileName(file);
        return CommonResult.success(
                ragDocumentService.uploadText(fileName, readTextContent(file)));
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
        if (fileName == null || fileName.isBlank()) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_READ_FAILED);
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        boolean supported = TEXT_FILE_EXTENSIONS.stream().anyMatch(lowerCaseName::endsWith);
        if (!supported) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_TYPE_UNSUPPORTED);
        }
        return fileName;
    }

    /**
     * 按 UTF-8 读取文件文本。
     *
     * @param file 上传文件
     * @return 文件文本
     * @throws ServiceException 读取失败或编码不是 UTF-8
     */
    private String readTextContent(MultipartFile file) {
        // 大小上限由 RagClient 按配置统一把关（同一份配置只读一处），这里只负责解码
        try {
            // 用 getBytes 再显式解码，而不是 getInputStream：MultipartFile 允许重复读取，
            // 且显式解码能把「非 UTF-8 文件」拦截成参数错误而不是让上游收到乱码
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (IOException readError) {
            // CharacterCodingException 是 IOException 的子类，两者处置一致：都是内容不可读
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_READ_FAILED);
        }
    }
}
