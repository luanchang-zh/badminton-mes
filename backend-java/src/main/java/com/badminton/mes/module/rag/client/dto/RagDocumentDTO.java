package com.badminton.mes.module.rag.client.dto;

import com.badminton.mes.module.rag.enums.RagDocumentStatus;

/**
 * 上游文档的一次快照，供列表展示与状态轮询。
 *
 * <p>{@code rawStatus} 保留上游原始状态字符串，{@code status} 是归一化结果：
 * 前者便于排查上游行为，后者供前端展示，两者都保留避免丢失信息。
 *
 * @param docId      上游文档标识
 * @param fileName   文件名
 * @param rawStatus  上游原始状态字符串
 * @param status     归一化后的状态
 * @author 张竹灏
 * @date 2026/11/08
 */
public record RagDocumentDTO(
        String docId,
        String fileName,
        String rawStatus,
        RagDocumentStatus status) {

    /**
     * 紧凑构造器：状态为空时归入 {@link RagDocumentStatus#UNKNOWN}。
     *
     * <p>上层会直接调用 {@code status.name()}，空值会让列表接口整页 500。
     */
    public RagDocumentDTO {
        status = status == null ? RagDocumentStatus.UNKNOWN : status;
    }
}
