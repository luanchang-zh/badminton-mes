package com.badminton.mes.module.rag.controller.vo;

import java.time.LocalDateTime;
import com.badminton.mes.module.rag.dal.entity.RagConversation;

public record RagConversationVO(Long id, String title, Long activeMessageId,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static RagConversationVO from(RagConversation c) {
        return new RagConversationVO(c.getId(), c.getTitle(), c.getActiveMessageId(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
