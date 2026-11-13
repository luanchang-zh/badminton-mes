package com.badminton.mes.module.rag.controller;

import com.badminton.mes.common.core.*;
import com.badminton.mes.module.rag.controller.vo.*;
import com.badminton.mes.module.rag.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 本人的知识库会话；全部身份从 MES 会话取得。 */
@RestController
@RequestMapping("/api/rag")
public class RagConversationController {
    private final RagConversationService conversations;
    private final RagChatService chat;

    public RagConversationController(RagConversationService conversations, RagChatService chat) {
        this.conversations = conversations; this.chat = chat;
    }

    public record CreateRequest(@Size(max = 120) String title) { }
    public record RenameRequest(@NotBlank @Size(max = 120) String title) { }
    public record RetryRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId) { }

    @PostMapping("/conversations")
    public CommonResult<RagConversationVO> create(@Valid @RequestBody(required = false) CreateRequest req) {
        return CommonResult.success(conversations.create(req == null ? null : req.title()));
    }

    @GetMapping("/conversations")
    public CommonResult<PageResult<RagConversationVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return CommonResult.success(conversations.list(pageNo, pageSize));
    }

    @GetMapping("/conversations/{id}/messages")
    public CommonResult<PageResult<RagMessageVO>> messages(@PathVariable @Positive Long id,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int pageSize) {
        return CommonResult.success(conversations.listMessages(id, pageNo, pageSize));
    }

    @PatchMapping("/conversations/{id}")
    public CommonResult<RagConversationVO> rename(@PathVariable @Positive Long id,
            @Valid @RequestBody RenameRequest req) {
        return CommonResult.success(conversations.rename(id, req.title()));
    }

    @DeleteMapping("/conversations/{id}")
    public CommonResult<Void> delete(@PathVariable @Positive Long id) {
        chat.delete(id);
        return CommonResult.success(null);
    }

    @PostMapping("/messages/{id}/cancel")
    public CommonResult<RagMessageVO> cancel(@PathVariable @Positive Long id) {
        return CommonResult.success(chat.cancel(id));
    }

    @PostMapping(value = "/messages/{id}/retry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retry(@PathVariable @Positive Long id, @Valid @RequestBody RetryRequest req) {
        return chat.retry(id, req.requestId());
    }
}
