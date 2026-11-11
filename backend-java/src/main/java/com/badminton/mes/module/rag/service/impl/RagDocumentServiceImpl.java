package com.badminton.mes.module.rag.service.impl;

import java.util.List;

import com.badminton.mes.module.rag.client.RagClient;
import com.badminton.mes.module.rag.client.dto.RagDocumentDTO;
import com.badminton.mes.module.rag.client.dto.RagDocumentContent;
import com.badminton.mes.module.rag.service.RagDocumentService;
import com.badminton.mes.module.rag.service.RagTenantResolver;

import org.springframework.stereotype.Service;

/**
 * 知识库文档 Service 实现。
 *
 * <p>本层只做两件事：解析租户、调用客户端。上传大小与租户格式校验都在
 * {@link RagClient} 里做——它们是「发请求前必须成立的条件」，放在最贴近请求的
 * 位置只需维护一份实现。
 *
 * <p>真实的隔离效果由上游按租户前缀分路由保证，本层不做数据过滤：
 * 在这里再过滤一次等于把隔离逻辑复制到两个地方，反而更容易漏。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@Service
public class RagDocumentServiceImpl implements RagDocumentService {

    /** 租户解析 */
    private final RagTenantResolver tenantResolver;

    /** 上游同步客户端 */
    private final RagClient ragClient;

    /**
     * 构造器注入，依赖不可变。
     *
     * @param tenantResolver 租户解析
     * @param ragClient      上游同步客户端
     */
    public RagDocumentServiceImpl(RagTenantResolver tenantResolver, RagClient ragClient) {
        this.tenantResolver = tenantResolver;
        this.ragClient = ragClient;
    }

    @Override
    public String uploadText(String fileSource, String content) {
        String tenantId = tenantResolver.resolveTenantId();
        return ragClient.uploadText(tenantId, fileSource, content);
    }

    @Override
    public List<RagDocumentDTO> listDocuments() {
        String tenantId = tenantResolver.resolveTenantId();
        return ragClient.listDocuments(tenantId);
    }

    @Override
    public RagDocumentContent readDocument(String docId) {
        return ragClient.readDocument(tenantResolver.resolveTenantId(), docId);
    }

    @Override
    public String uploadVersion(String docId, String fileSource, String content) {
        String tenantId = tenantResolver.resolveTenantId();
        ragClient.readDocument(tenantId, docId);
        int dot = fileSource.lastIndexOf('.');
        String suffix = "-v" + java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String versionedName = dot > 0 ? fileSource.substring(0, dot) + suffix + fileSource.substring(dot)
                : fileSource + suffix;
        return ragClient.uploadText(tenantId, versionedName, content);
    }

    @Override
    public void deleteDocument(String docId) {
        String tenantId = tenantResolver.resolveTenantId();
        ragClient.deleteDocument(tenantId, docId);
    }

    @Override
    public void reprocessFailedDocuments() {
        String tenantId = tenantResolver.resolveTenantId();
        ragClient.reprocessFailedDocuments(tenantId);
    }
}
