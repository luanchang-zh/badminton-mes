package com.badminton.mes.module.rag.service;

import java.util.List;

import com.badminton.mes.module.rag.client.dto.RagDocumentDTO;

/**
 * 知识库文档 Service。
 *
 * <p>所有方法都不接收租户参数：租户由本层内部调用 {@link RagTenantResolver} 从登录上下文解析。
 * 这是签名层面的越权防线——调用方即使想指定别人的租户也无从下手。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
public interface RagDocumentService {

    /**
     * 上传一段文本作为当前租户的文档。
     *
     * @param fileSource 文档来源标识，同名会被上游判为冲突
     * @param content    文本内容
     * @return 上游返回的任务追踪号，用于查询处理进度
     */
    String uploadText(String fileSource, String content);

    /**
     * 列出当前租户的文档。
     *
     * @return 文档列表，状态已归一化
     */
    List<RagDocumentDTO> listDocuments();

    /**
     * 删除当前租户下的指定文档。
     *
     * @param docId 文档标识
     */
    void deleteDocument(String docId);

    /**
     * 重新处理当前租户下所有失败、待处理或中断的文档。
     *
     * <p>上游不支持按文档过滤：一次调用会把整个租户的失败文档全部重跑。
     * 若只需重建单个文档，只能先删除再按原文件名重新上传。
     */
    void reprocessFailedDocuments();
}
