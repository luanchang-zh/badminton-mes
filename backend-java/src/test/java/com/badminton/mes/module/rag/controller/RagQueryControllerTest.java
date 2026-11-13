package com.badminton.mes.module.rag.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.badminton.mes.common.security.AuthInterceptor;
import com.badminton.mes.module.rag.client.dto.RagQueryResult;
import com.badminton.mes.module.rag.enums.RagQueryMode;
import com.badminton.mes.module.rag.service.RagQueryService;
import com.badminton.mes.module.rag.service.RagChatService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 问答 Controller Web 切片测试。
 *
 * <p>重点是「非法 mode 不会流到 Service」与「流式接口返回 SSE 而不是 JSON」：
 * 前者保证上游永远收不到非法取值，后者保证前端能建立事件流连接。
 */
@WebMvcTest(RagQueryController.class)
class RagQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagQueryService ragQueryService;

    @MockitoBean
    private RagChatService chatService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void permitAllRequests() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("同步问答：返回回答、上下文与模式")
    void queryReturnsAnswer() throws Exception {
        when(ragQueryService.query("如何点检", RagQueryMode.MIX))
                .thenReturn(new RagQueryResult("按 SOP 执行", List.of("片段一"), "mix"));

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "如何点检", "mode": "mix"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.answer").value("按 SOP 执行"))
                .andExpect(jsonPath("$.data.contexts[0]").value("片段一"))
                .andExpect(jsonPath("$.data.mode").value("mix"));
    }

    @Test
    @DisplayName("同步问答：未指定模式时按缺省模式转发")
    void queryUsesDefaultMode() throws Exception {
        when(ragQueryService.query("问题", RagQueryMode.DEFAULT_MODE))
                .thenReturn(new RagQueryResult("回答", List.of(), "naive"));

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"问题\"}"))
                .andExpect(status().isOk());

        verify(ragQueryService).query("问题", RagQueryMode.DEFAULT_MODE);
    }

    @Test
    @DisplayName("同步问答：非法模式由枚举解析抛 A0400，不进入 Service")
    void queryRejectsInvalidMode() throws Exception {
        // 非法取值在 VO.effectiveMode() 里就被 RagQueryMode.parse 拦下，
        // 不再依赖手抄的正则，因此这里断言的是「Service 从未被调用」而不是某条字段错误
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "问题", "mode": "semantic"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"))
                .andExpect(jsonPath("$.userTip").isNotEmpty());

        verify(ragQueryService, never()).query(any(), any());
    }

    @Test
    @DisplayName("同步问答：空问题返回 A0400")
    void queryRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));

        verify(ragQueryService, never()).query(any(), any());
    }

    @Test
    @DisplayName("流式问答：返回 SSE 而不是 JSON")
    void streamQueryReturnsSseEmitter() throws Exception {
        when(ragQueryService.streamQuery(eq("问题"), any(RagQueryMode.class)))
                .thenReturn(new SseEmitter(1000L));

        mockMvc.perform(post("/api/rag/query/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"question\":\"问题\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }
}
