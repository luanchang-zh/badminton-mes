package com.badminton.mes.module.rag.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.client.*;
import com.badminton.mes.module.rag.client.dto.*;
import com.badminton.mes.module.rag.config.RagProperties;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.controller.vo.*;
import com.badminton.mes.module.rag.enums.RagQueryMode;
import com.badminton.mes.module.rag.sse.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.PreDestroy;

/** 有会话的生成编排。同步响应也读取上游流，以便同样支持主动取消。 */
@Service
public class RagChatService {
    private final RagConversationService conversations;
    private final RagStreamClient client;
    private final ExecutorService executor;
    private final RagProperties properties;
    private final ConcurrentMap<Long, Active> active = new ConcurrentHashMap<>();

    public RagChatService(RagConversationService conversations, RagStreamClient client,
                           @Qualifier("ragStreamExecutor") ExecutorService executor, RagProperties properties) {
        this.conversations = conversations; this.client = client;
        this.executor = executor; this.properties = properties;
    }

    public RagChatReplyVO query(Long id, String requestId, String question, RagQueryMode mode) {
        var turn = conversations.begin(id, requestId, question, mode);
        if (!turn.replay()) {
            Active task = register(turn);
            try {
                RagQueryResult result = consume(task, null);
                conversations.progress(turn, result, task.cancellation.isCancelled() ? "CANCELLED" : "COMPLETED", null);
            } catch (RuntimeException error) {
                fail(task, error);
                throw error;
            } finally {
                close(task);
            }
        }
        return new RagChatReplyVO(turn.conversationId(), turn.userMessageId(), turn.assistantMessageId(),
                conversations.message(turn.assistantMessageId()));
    }

    public SseEmitter stream(Long id, String requestId, String question, RagQueryMode mode) {
        return start(conversations.begin(id, requestId, question, mode));
    }

    public SseEmitter retry(Long messageId, String requestId) {
        return start(conversations.beginRetry(messageId, requestId));
    }

    public RagMessageVO cancel(Long messageId) {
        // 先校验归属，避免通过 active map 操作其他用户的流。
        conversations.message(messageId);
        Active task = active.get(messageId);
        if (task != null) {
            task.cancellation.cancel();
            conversations.progress(task.turn, task.snapshot(), "CANCELLED", "已停止生成");
        }
        return conversations.cancel(messageId);
    }

    public void delete(Long conversationId) {
        Long messageId = conversations.activeMessage(conversationId);
        if (messageId != null) cancel(messageId);
        conversations.delete(conversationId);
    }

    private SseEmitter start(RagConversationService.Turn turn) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeout().toMillis());
        if (turn.replay()) {
            replay(turn, emitter);
            return emitter;
        }
        Active task = register(turn);
        emitter.onCompletion(() -> stop(task));
        emitter.onTimeout(() -> { stop(task); emitter.complete(); });
        emitter.onError(error -> stop(task));
        try {
            executor.execute(() -> run(task, emitter));
        } catch (RuntimeException rejected) {
            fail(task, rejected);
            close(task);
            throw new ServiceException(RagErrorCodeConstants.UPSTREAM_UNAVAILABLE);
        }
        return emitter;
    }

    private void run(Active task, SseEmitter emitter) {
        try {
            send(emitter, RagSseEventNames.META, meta(task.turn));
            RagQueryResult result = consume(task, emitter);
            String state = task.cancellation.isCancelled() ? "CANCELLED" : "COMPLETED";
            RagMessageVO saved = conversations.progress(task.turn, result, state,
                    state.equals("CANCELLED") ? "已停止生成" : null);
            if (saved != null) send(emitter, RagSseEventNames.DONE, saved);
        } catch (RuntimeException error) {
            fail(task, error);
            if (!task.cancellation.isCancelled()) {
                var code = error instanceof ServiceException s ? s.getErrorCode() : RagErrorCodeConstants.UPSTREAM_ERROR;
                try { send(emitter, RagSseEventNames.ERROR,
                        RagSsePayloads.error(code.code(), code.message(), code.userTip())); }
                catch (RuntimeException ignored) { /* 客户端已断开，状态仍已保存。 */ }
            }
        } finally {
            emitter.complete();
            close(task);
        }
    }

    private RagQueryResult consume(Active task, SseEmitter emitter) {
        if (task.cancellation.isCancelled()) return task.snapshot();
        return client.streamQuery(task.turn.tenantId(), task.turn.question(), task.turn.mode(), task.turn.history(),
                line -> accept(task, emitter, line), task.cancellation);
    }

    private void accept(Active task, SseEmitter emitter, RagStreamLine line) {
        if (line.kind() == RagStreamLine.Kind.ERROR) throw new ServiceException(RagErrorCodeConstants.UPSTREAM_ERROR);
        task.append(line);
        long now = System.nanoTime();
        if (now - task.lastSave > TimeUnit.SECONDS.toNanos(1)) {
            conversations.progress(task.turn, task.snapshot(), "GENERATING", null);
            task.lastSave = now;
        }
        if (emitter == null) return;
        if (!line.referenceDetails().isEmpty()) send(emitter, RagSseEventNames.REFERENCE_DETAILS, line.referenceDetails());
        if (!line.references().isEmpty()) send(emitter, RagSseEventNames.REFERENCES, line.references());
        if (line.content() != null) send(emitter, RagSseEventNames.DELTA, line.content());
    }

    private void replay(RagConversationService.Turn turn, SseEmitter emitter) {
        RagMessageVO message = conversations.message(turn.assistantMessageId());
        send(emitter, RagSseEventNames.META, meta(turn));
        if (!message.references().isEmpty()) send(emitter, RagSseEventNames.REFERENCE_DETAILS, message.references());
        if (!message.content().isEmpty()) send(emitter, RagSseEventNames.DELTA, message.content());
        send(emitter, RagSseEventNames.DONE, message);
        emitter.complete();
    }

    private Map<String, Long> meta(RagConversationService.Turn turn) {
        return Map.of("conversationId", turn.conversationId(), "userMessageId", turn.userMessageId(),
                "assistantMessageId", turn.assistantMessageId());
    }

    private Active register(RagConversationService.Turn turn) {
        Active task = new Active(turn);
        active.put(turn.assistantMessageId(), task);
        return task;
    }

    private void stop(Active task) {
        task.cancellation.cancel();
        conversations.progress(task.turn, task.snapshot(), "CANCELLED", "已停止生成");
    }

    private void fail(Active task, RuntimeException error) {
        boolean cancelled = task.cancellation.isCancelled();
        String tip = cancelled ? "已停止生成" : error instanceof ServiceException s
                ? s.getErrorCode().userTip() : "知识库处理失败，请重试";
        conversations.progress(task.turn, task.snapshot(), cancelled ? "CANCELLED" : "FAILED", tip);
    }

    private void close(Active task) {
        task.cancellation.cancel();
        active.remove(task.turn.assistantMessageId(), task);
    }

    /** 服务关闭时释放仍在读取的上游句柄；遗留状态在下次启动时统一恢复。 */
    @PreDestroy
    public void closeActiveStreams() {
        active.values().forEach(task -> task.cancellation.cancel());
        active.clear();
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try { emitter.send(SseEmitter.event().name(name).data(data)); }
        catch (IOException | IllegalStateException error) {
            throw new ServiceException(RagErrorCodeConstants.STREAM_START_FAILED);
        }
    }

    private static final class Active {
        private final RagConversationService.Turn turn;
        private final StreamCancellation cancellation = new StreamCancellation();
        private final StringBuilder answer = new StringBuilder();
        private List<String> contexts = List.of();
        private List<RagReference> references = List.of();
        private long lastSave = System.nanoTime();
        private Active(RagConversationService.Turn turn) {
            this.turn = turn;
        }

        private synchronized void append(RagStreamLine line) {
            if (line.content() != null) {
                if (answer.length() + line.content().length() > 262144)
                    throw new ServiceException(RagErrorCodeConstants.UPSTREAM_ERROR);
                answer.append(line.content());
            }
            if (!line.references().isEmpty()) contexts = line.references();
            if (!line.referenceDetails().isEmpty()) references = line.referenceDetails();
        }

        private synchronized RagQueryResult snapshot() {
            return new RagQueryResult(answer.toString(), contexts, turn.mode().getValue(), references);
        }
    }
}
