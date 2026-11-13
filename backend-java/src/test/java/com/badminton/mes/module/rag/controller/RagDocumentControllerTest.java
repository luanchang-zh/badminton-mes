package com.badminton.mes.module.rag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import com.badminton.mes.common.security.AuthInterceptor;
import com.badminton.mes.common.security.RequiresRoles;
import com.badminton.mes.common.security.RoleCodeConstants;
import com.badminton.mes.module.rag.client.dto.RagDocumentDTO;
import com.badminton.mes.module.rag.enums.RagDocumentStatus;
import com.badminton.mes.module.rag.service.RagDocumentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 文档 Controller Web 切片测试。
 *
 * <p>三组要点：
 * <ol>
 *   <li>上传只接受文本类扩展名与 UTF-8 内容，其余在 Controller 就被拒绝；</li>
 *   <li>写操作限角色，查询不限；</li>
 *   <li><b>所有方法都不接收租户参数</b>——用反射锁死这条签名约束，
 *       防止后续有人「顺手」加一个 tenantId 参数把越权口子打开。</li>
 * </ol>
 */
@WebMvcTest(RagDocumentController.class)
class RagDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagDocumentService ragDocumentService;

    /** Mock 掉鉴权拦截器：Web 切片无 Redis，鉴权逻辑由拦截器自身测试覆盖 */
    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void permitAllRequests() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("上传文本文件：内容被转成字符串后转发 Service")
    void uploadDocumentForwardsTextContent() throws Exception {
        when(ragDocumentService.uploadText(eq("sop.md"), eq("设备点检标准"))).thenReturn("doc-1");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sop.md", "text/markdown", "设备点检标准".getBytes());

        mockMvc.perform(multipart("/api/rag/documents").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data").value("doc-1"));
    }

    @Test
    @DisplayName("上传二进制扩展名：返回 A0400 且不调用 Service")
    void uploadDocumentRejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "manual.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/rag/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"))
                .andExpect(jsonPath("$.userTip").isNotEmpty());

        verify(ragDocumentService, never()).uploadText(any(), any());
    }

    @Test
    @DisplayName("上传非 UTF-8 内容：返回 A0400 且不调用 Service")
    void uploadDocumentRejectsNonUtf8Content() throws Exception {
        // 0xFF 0xFE 是 UTF-16 的 BOM，无法按 UTF-8 解码
        MockMultipartFile file = new MockMultipartFile(
                "file", "broken.txt", "text/plain", new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00});

        mockMvc.perform(multipart("/api/rag/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));

        verify(ragDocumentService, never()).uploadText(any(), any());
    }

    @Test
    @DisplayName("列表：把上游状态转换为中文描述")
    void listDocumentsMapsStatus() throws Exception {
        when(ragDocumentService.listDocuments()).thenReturn(List.of(
                new RagDocumentDTO("doc-1", "sop.md", "processed", RagDocumentStatus.COMPLETED),
                new RagDocumentDTO("doc-2", "b.md", "weird", RagDocumentStatus.UNKNOWN)));

        mockMvc.perform(get("/api/rag/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].statusText").value("已完成"))
                .andExpect(jsonPath("$.data[1].status").value("UNKNOWN"));
    }

    @Test
    @DisplayName("列表：无数据时返回空数组而不是 null(API-002)")
    void listDocumentsReturnsEmptyArray() throws Exception {
        when(ragDocumentService.listDocuments()).thenReturn(List.of());

        mockMvc.perform(get("/api/rag/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("删除：转发生成的文档标识")
    void deleteDocumentForwardsDocId() throws Exception {
        mockMvc.perform(delete("/api/rag/documents/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));

        verify(ragDocumentService).deleteDocument("doc-1");
    }

    @Test
    @DisplayName("重新处理失败文档：无请求体，直接转发 Service")
    void reprocessFailedForwardsToService() throws Exception {
        mockMvc.perform(post("/api/rag/documents/reprocess-failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));

        verify(ragDocumentService).reprocessFailedDocuments();
    }

    @Test
    @DisplayName("角色契约：写动作限管理员与工艺工程师，查询不限")
    void writeActionsRequireAdminOrCraftEngineer() {
        List<String> writeActions = List.of(
                "uploadDocument", "uploadText", "deleteDocument", "reprocessFailed");

        for (Method method : RagDocumentController.class.getDeclaredMethods()) {
            RequiresRoles requiresRoles = method.getAnnotation(RequiresRoles.class);
            if (writeActions.contains(method.getName())) {
                assertThat(requiresRoles).as("写动作 %s 必须声明角色限制", method.getName()).isNotNull();
                assertThat(requiresRoles.value()).containsExactlyInAnyOrder(
                        RoleCodeConstants.ADMIN, RoleCodeConstants.CRAFT_ENGINEER);
            } else if ("listDocuments".equals(method.getName())) {
                assertThat(requiresRoles).as("查询动作登录即可，不应限角色").isNull();
            }
        }
    }

    @Test
    @DisplayName("签名契约：任何对外方法都不接收租户参数")
    void noMethodAcceptsTenantParameter() {
        for (Method method : RagDocumentController.class.getDeclaredMethods()) {
            // 只检查 Controller 声明的对外方法；私有辅助方法不构成入口
            if (method.isSynthetic() || method.getName().startsWith("require")
                    || method.getName().startsWith("read")) {
                continue;
            }
            for (Parameter parameter : method.getParameters()) {
                assertThat(parameter.getName().toLowerCase())
                        .as("方法 %s 的参数名不得暗示租户，租户只能由服务端解析", method.getName())
                        .doesNotContain("tenant")
                        .doesNotContain("workspace");
            }
        }
    }
}
