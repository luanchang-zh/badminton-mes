package com.badminton.mes.module.rag.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.client.RagClient;
import com.badminton.mes.module.rag.client.dto.RagDocumentDTO;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.enums.RagDocumentStatus;
import com.badminton.mes.module.rag.service.RagTenantResolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 文档 Service 测试。
 *
 * <p>核心断言只有一个：<b>Service 传给客户端的租户标识来自 Resolver</b>。
 * 一旦哪天有人给 Service 方法加了 tenant 参数，这组用例仍会通过，
 * 但 Controller 层「不接收租户参数」的签名约束会在 Web 测试里报错，
 * 两处合起来才能守住这条边界。
 */
@ExtendWith(MockitoExtension.class)
class RagDocumentServiceImplTest {

    @Test
    void downloadUsesOnlyTheResolvedTenant() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        var content = new com.badminton.mes.module.rag.client.dto.RagDocumentContent("doc-a", "资料.md", "原文");
        when(ragClient.readDocument(TENANT, "doc-a")).thenReturn(content);
        assertThat(service.readDocument("doc-a")).isEqualTo(content);
    }

    @Test
    void newVersionPreservesOriginalAndUsesADistinctFileSource() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        service.uploadVersion("doc-a", "工艺.md", "新版正文");
        verify(ragClient).readDocument(TENANT, "doc-a");
        verify(ragClient).uploadText(eq(TENANT),
                org.mockito.ArgumentMatchers.matches("工艺-v[0-9]{14}-[a-f0-9]{8}\\.md"), eq("新版正文"));
        verify(ragClient, org.mockito.Mockito.never()).deleteDocument(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 解析器返回的租户 */
    private static final String TENANT = "ws_001";

    @Mock
    private RagTenantResolver tenantResolver;

    @Mock
    private RagClient ragClient;

    @InjectMocks
    private RagDocumentServiceImpl service;

    @Test
    @DisplayName("上传时用解析出的租户调用客户端")
    void shouldUploadWithResolvedTenant() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        when(ragClient.uploadText(TENANT, "sop.md", "内容")).thenReturn("doc-1");

        assertThat(service.uploadText("sop.md", "内容")).isEqualTo("doc-1");
        verify(ragClient).uploadText(TENANT, "sop.md", "内容");
    }

    @Test
    @DisplayName("列表时用解析出的租户调用客户端")
    void shouldListWithResolvedTenant() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        List<RagDocumentDTO> documents = List.of(new RagDocumentDTO(
                "doc-1", "sop.md", "processed", RagDocumentStatus.COMPLETED));
        when(ragClient.listDocuments(TENANT)).thenReturn(documents);

        assertThat(service.listDocuments()).isEqualTo(documents);
        verify(ragClient).listDocuments(TENANT);
    }

    @Test
    @DisplayName("删除时用解析出的租户调用客户端")
    void shouldDeleteWithResolvedTenant() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);

        service.deleteDocument("doc-1");

        verify(ragClient).deleteDocument(TENANT, "doc-1");
    }

    @Test
    @DisplayName("重新处理失败文档时用解析出的租户调用客户端")
    void shouldReprocessFailedWithResolvedTenant() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);

        service.reprocessFailedDocuments();

        verify(ragClient).reprocessFailedDocuments(TENANT);
    }

    @Test
    @DisplayName("租户解析失败时不会调用客户端")
    void shouldNotCallClientWhenTenantResolutionFails() {
        when(tenantResolver.resolveTenantId())
                .thenThrow(new ServiceException(RagErrorCodeConstants.TENANT_NOT_BOUND));

        assertThatThrownBy(service::listDocuments)
                .isInstanceOf(ServiceException.class);

        // 未解析出租户就发请求，等于用空租户访问全局命名空间，必须避免
        verifyNoInteractions(ragClient);
    }
}
