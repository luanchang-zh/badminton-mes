package com.badminton.mes.module.rag.controller.vo;

import java.time.LocalDateTime;
import java.util.List;
import com.badminton.mes.module.rag.client.dto.RagReference;

public record RagMessageVO(Long id, Long conversationId, String role, String content, String mode,
                           String status, Long replyToId, List<RagReference> references,
                           String errorTip, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
