package com.badminton.mes.module.rag.client.dto;

/** 由 MES 已保存的完整问答构建的上游历史。 */
public record RagHistoryMessage(String role, String content) {
}
