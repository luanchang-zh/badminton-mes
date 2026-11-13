package com.badminton.mes.module.rag.controller.vo;

/** 有会话的同步回答；id 可直接用于消息恢复与取消。 */
public record RagChatReplyVO(Long conversationId, Long userMessageId, Long assistantMessageId,
                             RagMessageVO message) {
}
