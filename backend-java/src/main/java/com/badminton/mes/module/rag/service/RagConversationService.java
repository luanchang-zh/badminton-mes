package com.badminton.mes.module.rag.service;

import java.time.LocalDateTime;
import java.util.*;
import com.badminton.mes.common.core.PageResult;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.core.GlobalErrorCodeConstants;
import com.badminton.mes.common.security.SecurityContextHolder;
import com.badminton.mes.module.rag.client.dto.*;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.controller.vo.*;
import com.badminton.mes.module.rag.dal.entity.*;
import com.badminton.mes.module.rag.dal.repository.*;
import com.badminton.mes.module.rag.enums.RagQueryMode;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 短事务管理会话和生成记录。上游调用始终在事务之外执行。 */
@Service
public class RagConversationService {
    private final RagConversationRepository conversations;
    private final RagMessageRepository messages;
    private final RagTenantResolver tenants;
    private final ObjectMapper json;

    public RagConversationService(RagConversationRepository conversations, RagMessageRepository messages,
                                   RagTenantResolver tenants, ObjectMapper json) {
        this.conversations = conversations;
        this.messages = messages;
        this.tenants = tenants;
        this.json = json;
    }

    /** 请求线程解析出的身份与生成参数，可安全传给后台线程。 */
    public record Turn(Long conversationId, Long userId, String tenantId, Long userMessageId,
                       Long assistantMessageId, String question, RagQueryMode mode,
                       List<RagHistoryMessage> history, boolean replay) { }

    @Transactional
    public RagConversationVO create(String title) {
        RagConversation c = new RagConversation();
        c.setUserId(SecurityContextHolder.getRequiredLoginUserId());
        c.setTenantId(tenants.resolveTenantId());
        c.setTitle(title == null || title.isBlank() ? "新对话" : title.strip());
        return RagConversationVO.from(conversations.save(c));
    }

    @Transactional(readOnly = true)
    public PageResult<RagConversationVO> list(int pageNo, int pageSize) {
        Long userId = SecurityContextHolder.getRequiredLoginUserId();
        String tenant = tenants.resolveTenantId();
        var page = conversations.findByUserIdAndTenantId(userId, tenant,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
        if (page.getTotalPages() > 0 && pageNo > page.getTotalPages()) {
            return list(page.getTotalPages(), pageSize);
        }
        return PageResult.of(page.getContent().stream().map(RagConversationVO::from).toList(),
                page.getTotalElements(), pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public PageResult<RagMessageVO> listMessages(Long id, int pageNo, int pageSize) {
        owned(id, false);
        var page = messages.findByConversationId(id,
                PageRequest.of(pageNo - 1, pageSize, Sort.by("id")));
        if (page.getTotalPages() > 0 && pageNo > page.getTotalPages()) {
            return listMessages(id, page.getTotalPages(), pageSize);
        }
        return PageResult.of(page.getContent().stream().map(this::view).toList(),
                page.getTotalElements(), pageNo, pageSize);
    }

    @Transactional
    public RagConversationVO rename(Long id, String title) {
        RagConversation c = owned(id, true);
        c.setTitle(title.strip());
        c.setUpdatedAt(LocalDateTime.now());
        return RagConversationVO.from(c);
    }

    @Transactional
    public void delete(Long id) {
        RagConversation c = owned(id, true);
        if (c.getActiveMessageId() != null) {
            throw new ServiceException(RagErrorCodeConstants.GENERATION_BUSY);
        }
        messages.deleteByConversationId(id);
        conversations.delete(c);
    }

    @Transactional
    public Turn begin(Long id, String requestId, String question, RagQueryMode mode) {
        RagConversation c = owned(id, true);
        requireRequestId(requestId);
        var previous = messages.findByConversationIdAndRequestId(id, requestId);
        if (previous.isPresent()) {
            RagMessage original = messages.findById(previous.get().getReplyToId()).orElseThrow(this::missing);
            if (!original.getContent().equals(question) || !previous.get().getMode().equals(mode.getValue())) {
                throw new ServiceException(RagErrorCodeConstants.REQUEST_CONFLICT);
            }
            return replay(c, original, previous.get());
        }
        requireIdle(c);
        RagMessage user = newMessage(c, "user", question, mode, "COMPLETED");
        messages.saveAndFlush(user);
        if (c.getTitle().equals("新对话")) {
            c.setTitle(question.substring(0, Math.min(60, question.length())));
        }
        return start(c, user, mode, requestId);
    }

    @Transactional
    public Turn beginRetry(Long messageId, String requestId) {
        RagMessage old = ownedMessage(messageId);
        RagConversation c = owned(old.getConversationId(), true);
        requireRequestId(requestId);
        if (!old.getRole().equals("assistant") || !Set.of("FAILED", "CANCELLED").contains(old.getStatus())) {
            throw new ServiceException(RagErrorCodeConstants.RETRY_NOT_ALLOWED);
        }
        RagMessage user = messages.findById(old.getReplyToId()).orElseThrow(this::missing);
        var previous = messages.findByConversationIdAndRequestId(c.getId(), requestId);
        if (previous.isPresent()) {
            if (!previous.get().getReplyToId().equals(user.getId())) {
                throw new ServiceException(RagErrorCodeConstants.REQUEST_CONFLICT);
            }
            return replay(c, user, previous.get());
        }
        requireIdle(c);
        return start(c, user, RagQueryMode.parse(old.getMode()), requestId);
    }

    private Turn start(RagConversation c, RagMessage user, RagQueryMode mode, String requestId) {
        RagMessage assistant = newMessage(c, "assistant", "", mode, "GENERATING");
        assistant.setRequestId(requestId);
        assistant.setReplyToId(user.getId());
        messages.saveAndFlush(assistant);
        c.setActiveMessageId(assistant.getId());
        c.setUpdatedAt(LocalDateTime.now());
        return turn(c, user, assistant, false);
    }

    private Turn replay(RagConversation c, RagMessage user, RagMessage assistant) {
        if (assistant.getStatus().equals("GENERATING")) {
            throw new ServiceException(RagErrorCodeConstants.GENERATION_BUSY);
        }
        return turn(c, user, assistant, true);
    }

    private Turn turn(RagConversation c, RagMessage user, RagMessage assistant, boolean replay) {
        return new Turn(c.getId(), c.getUserId(), c.getTenantId(), user.getId(), assistant.getId(),
                user.getContent(), RagQueryMode.parse(assistant.getMode()), history(c.getId(), user.getId()), replay);
    }

    private List<RagHistoryMessage> history(Long conversationId, Long beforeId) {
        List<RagMessage> recent = messages.findTop100ByConversationIdAndIdLessThanOrderByIdDesc(conversationId, beforeId);
        Map<Long, RagMessage> users = new HashMap<>();
        recent.stream().filter(m -> m.getRole().equals("user")).forEach(m -> users.put(m.getId(), m));
        Set<Long> seen = new HashSet<>();
        LinkedList<RagHistoryMessage> result = new LinkedList<>();
        int chars = 0;
        for (RagMessage answer : recent) {
            RagMessage user = users.get(answer.getReplyToId());
            if (!answer.getRole().equals("assistant") || !answer.getStatus().equals("COMPLETED")
                    || user == null || !seen.add(user.getId())) continue;
            int length = user.getContent().length() + answer.getContent().length();
            if (result.size() >= 12 || chars + length > 16000) break;
            result.addFirst(new RagHistoryMessage("assistant", answer.getContent()));
            result.addFirst(new RagHistoryMessage("user", user.getContent()));
            chars += length;
        }
        return List.copyOf(result);
    }

    @Transactional
    public RagMessageVO progress(Turn turn, RagQueryResult result, String status, String errorTip) {
        var conversation = conversations.lockOwned(turn.conversationId(), turn.userId());
        var found = messages.findById(turn.assistantMessageId());
        if (conversation.isEmpty() || found.isEmpty()) return null;
        RagMessage m = found.get();
        if (m.getStatus().equals("GENERATING")) {
            m.setContent(result.answer() == null ? "" : result.answer());
            m.setReferencesJson(json.writeValueAsString(result.references()));
            m.setStatus(status);
            m.setErrorTip(errorTip);
            m.setUpdatedAt(LocalDateTime.now());
            conversation.get().setUpdatedAt(m.getUpdatedAt());
            if (!status.equals("GENERATING")) conversation.get().setActiveMessageId(null);
        }
        return view(m);
    }

    @Transactional(readOnly = true)
    public RagMessageVO message(Long id) {
        return view(ownedMessage(id));
    }

    @Transactional(readOnly = true)
    public Long activeMessage(Long conversationId) {
        return owned(conversationId, false).getActiveMessageId();
    }

    @Transactional
    public RagMessageVO cancel(Long id) {
        Long conversationId = messages.findConversationIdById(id).orElseThrow(this::missing);
        RagConversation c = owned(conversationId, true);
        RagMessage m = messages.findById(id).orElseThrow(this::missing);
        if (m.getStatus().equals("GENERATING")) {
            m.setStatus("CANCELLED");
            m.setErrorTip("已停止生成");
            m.setUpdatedAt(LocalDateTime.now());
            c.setActiveMessageId(null);
            c.setUpdatedAt(m.getUpdatedAt());
        }
        return view(m);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterrupted() {
        messages.failInterruptedGenerations();
        conversations.clearInterruptedGenerations();
    }

    private RagConversation owned(Long id, boolean lock) {
        Long userId = SecurityContextHolder.getRequiredLoginUserId();
        RagConversation c = (lock ? conversations.lockOwned(id, userId)
                : conversations.findByIdAndUserId(id, userId)).orElseThrow(this::missing);
        if (!c.getTenantId().equals(tenants.resolveTenantId())) throw missing();
        return c;
    }

    private RagMessage ownedMessage(Long id) {
        RagMessage m = messages.findById(id).orElseThrow(this::missing);
        owned(m.getConversationId(), false);
        return m;
    }

    private RagMessage newMessage(RagConversation c, String role, String content, RagQueryMode mode, String status) {
        RagMessage m = new RagMessage();
        m.setConversationId(c.getId()); m.setRole(role); m.setContent(content);
        m.setMode(mode.getValue()); m.setStatus(status);
        return m;
    }

    private RagMessageVO view(RagMessage m) {
        List<RagReference> references = json.readValue(m.getReferencesJson(), new TypeReference<List<RagReference>>() { });
        return new RagMessageVO(m.getId(), m.getConversationId(), m.getRole(), m.getContent(), m.getMode(),
                m.getStatus(), m.getReplyToId(), references, m.getErrorTip(), m.getCreatedAt(), m.getUpdatedAt());
    }

    private void requireRequestId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new ServiceException(GlobalErrorCodeConstants.PARAM_ERROR, "requestId 需为 8～64 位字母、数字、下划线或连字符");
        }
    }

    private void requireIdle(RagConversation c) {
        if (c.getActiveMessageId() != null) throw new ServiceException(RagErrorCodeConstants.GENERATION_BUSY);
    }

    private ServiceException missing() {
        return new ServiceException(RagErrorCodeConstants.CONVERSATION_NOT_FOUND);
    }
}
