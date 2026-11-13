package com.badminton.mes.module.rag.controller;

import com.badminton.mes.common.core.CommonResult;
import com.badminton.mes.module.rag.controller.vo.RagQueryReqVO;
import com.badminton.mes.module.rag.controller.vo.RagQueryRespVO;
import com.badminton.mes.module.rag.service.RagQueryService;
import com.badminton.mes.module.rag.service.RagChatService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;

/**
 * 知识库问答 Controller。
 *
 * <p>问答接口登录即可，不限角色：浏览知识库内容是正常业务需求。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@RestController
@RequestMapping("/api/rag/query")
public class RagQueryController {

    private final RagQueryService ragQueryService;
    private final RagChatService chatService;

    /**
     * 构造器注入，依赖不可变。
     *
     * @param ragQueryService 问答 Service
     */
    public RagQueryController(RagQueryService ragQueryService, RagChatService chatService) {
        this.ragQueryService = ragQueryService;
        this.chatService = chatService;
    }

    /**
     * 同步问答，适合问题较短或前端不需要边生成边展示的场景。
     *
     * @param reqVO 问答请求
     * @return 回答与检索上下文
     */
    @PostMapping
    public CommonResult<?> query(@Valid @RequestBody RagQueryReqVO reqVO) {
        if (reqVO.conversationId() != null) {
            return CommonResult.success(chatService.query(reqVO.conversationId(), reqVO.requestId(),
                    reqVO.question(), reqVO.effectiveMode()));
        }
        return CommonResult.success(
                RagQueryRespVO.from(ragQueryService.query(
                        reqVO.question(), reqVO.effectiveMode())));
    }

    /**
     * 流式问答，以 SSE 逐段推送。
     *
     * <p>声明 {@code produces = text/event-stream} 是必需的，否则前端会按 JSON 解析而拿不到事件流。
     *
     * <p><b>前端必须用 {@code fetch} + {@code ReadableStream} 消费</b>，不能用浏览器原生
     * {@code EventSource}：后者只支持 GET 且无法携带请求体，而本接口是带问题正文的 POST。
     *
     * @param reqVO 问答请求
     * @return SSE 发射器
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuery(@Valid @RequestBody RagQueryReqVO reqVO) {
        if (reqVO.conversationId() != null) {
            return chatService.stream(reqVO.conversationId(), reqVO.requestId(), reqVO.question(), reqVO.effectiveMode());
        }
        return ragQueryService.streamQuery(reqVO.question(), reqVO.effectiveMode());
    }
}
