package com.badminton.mes.module.rag.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.client.RagClient;
import com.badminton.mes.module.rag.client.RagStreamClient;
import com.badminton.mes.module.rag.client.RagStreamLine;
import com.badminton.mes.module.rag.client.StreamCancellation;
import com.badminton.mes.module.rag.client.dto.RagGraphResult;
import com.badminton.mes.module.rag.client.dto.RagQueryResult;
import com.badminton.mes.module.rag.config.RagProperties;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.enums.RagQueryMode;
import com.badminton.mes.module.rag.service.RagQueryService;
import com.badminton.mes.module.rag.service.RagTenantResolver;
import com.badminton.mes.module.rag.sse.RagSseEventNames;
import com.badminton.mes.module.rag.sse.RagSsePayloads;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 知识库问答与图谱 Service 实现。
 *
 * <p><b>租户解析时机</b>：流式问答在请求线程上先把租户解析出来，再交给读取线程。
 * 登录上下文是 ThreadLocal，跨线程后取不到；若在读取线程里解析，
 * 会稳定地抛出「登录已过期」，且难以从日志看出原因。
 *
 * <p><b>错误处理</b>：请求线程阶段（解析租户）的错误照常抛给全局异常处理器，
 * 前端拿到普通 HTTP 错误；一旦 SSE 已开始，就改用 error 事件——
 * 此时响应头已发送，无法再改 HTTP 状态码。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@Service
public class RagQueryServiceImpl implements RagQueryService {

    private static final Logger logger = LoggerFactory.getLogger(RagQueryServiceImpl.class);

    /** 图谱展开深度下限；上游只校验 {@code ge=1}，下限由本系统保证 */
    private static final int MIN_GRAPH_DEPTH = 1;

    /** 图谱展开深度上限；深度每加一层 BFS 节点数增长很快，必须有硬上限 */
    private static final int MAX_GRAPH_DEPTH = 3;

    /** 租户解析 */
    private final RagTenantResolver tenantResolver;

    /** 上游同步客户端 */
    private final RagClient ragClient;

    /** 上游流式客户端 */
    private final RagStreamClient ragStreamClient;

    /** 流式读取执行器 */
    private final ExecutorService streamExecutor;

    /** 上游配置 */
    private final RagProperties properties;

    /**
     * 构造器注入。
     *
     * @param tenantResolver 租户解析
     * @param ragClient      上游同步客户端
     * @param ragStreamClient 上游流式客户端
     * @param streamExecutor 流式读取执行器，虚拟线程
     * @param properties     上游配置
     */
    public RagQueryServiceImpl(RagTenantResolver tenantResolver,
                               RagClient ragClient,
                               RagStreamClient ragStreamClient,
                               @Qualifier("ragStreamExecutor") ExecutorService streamExecutor,
                               RagProperties properties) {
        this.tenantResolver = tenantResolver;
        this.ragClient = ragClient;
        this.ragStreamClient = ragStreamClient;
        this.streamExecutor = streamExecutor;
        this.properties = properties;
    }

    @Override
    public RagQueryResult query(String question, RagQueryMode mode) {
        String tenantId = tenantResolver.resolveTenantId();
        RagQueryMode safeMode = mode == null ? RagQueryMode.DEFAULT_MODE : mode;
        return ragClient.query(tenantId, question, safeMode.getValue());
    }

    @Override
    public SseEmitter streamQuery(String question, RagQueryMode mode) {
        // 必须在请求线程解析：登录上下文是 ThreadLocal，读取线程里取不到
        String tenantId = tenantResolver.resolveTenantId();
        RagQueryMode safeMode = mode == null ? RagQueryMode.DEFAULT_MODE : mode;
        SseEmitter emitter = new SseEmitter(properties.getSseTimeout().toMillis());
        StreamCancellation cancellation = new StreamCancellation();

        // 客户端断开 / 超时 / 出错都要取消上游，否则模型会一直生成到结束
        emitter.onCompletion(cancellation::cancel);
        emitter.onTimeout(() -> {
            cancellation.cancel();
            // 超时后必须显式结束，否则连接会挂到容器的兜底超时，日志里只留一条难懂的警告
            emitter.complete();
        });
        emitter.onError(error -> cancellation.cancel());

        streamExecutor.execute(() -> runStream(emitter, cancellation, tenantId, question, safeMode));
        return emitter;
    }

    @Override
    public List<String> listGraphLabels() {
        String tenantId = tenantResolver.resolveTenantId();
        return ragClient.listGraphLabels(tenantId);
    }

    @Override
    public RagGraphResult getGraph(String label, int maxDepth) {
        // 深度约束只在这一层收敛：Controller 只负责参数存在性，其他调用方也受同一上限保护
        int safeDepth = Math.clamp(maxDepth, MIN_GRAPH_DEPTH, MAX_GRAPH_DEPTH);
        return ragClient.getGraph(tenantResolver.resolveTenantId(), label, safeDepth);
    }

    /**
     * 在读取线程上执行流式问答并写入 SSE。
     *
     * <p>无论成功、失败还是取消，都必须让 emitter 进入终态，否则连接会挂到超时。
     *
     * @param emitter       SSE 发射器
     * @param cancellation  取消信号
     * @param tenantId      已解析的租户标识
     * @param question      问题
     * @param mode          检索模式
     */
    private void runStream(SseEmitter emitter,
                           StreamCancellation cancellation,
                           String tenantId,
                           String question,
                           RagQueryMode mode) {
        try {
            RagQueryResult result = ragStreamClient.streamQuery(
                    tenantId, question, mode,
                    line -> forwardLine(emitter, tenantId, line),
                    cancellation);
            if (!cancellation.isCancelled()) {
                sendEvent(emitter, RagSseEventNames.DONE,
                        RagSsePayloads.done(result.answer(), result.mode()));
                emitter.complete();
            }
        } catch (ServiceException businessError) {
            // 业务异常信息里可能含上游状态码，userTip 才是给用户看的
            closeAfterError(emitter, businessError, tenantId);
        } catch (RuntimeException unexpectedError) {
            logger.error("[知识库流式问答失败] tenant: {}", tenantId, unexpectedError);
            closeAfterError(emitter,
                    new ServiceException(RagErrorCodeConstants.UPSTREAM_ERROR), tenantId);
        }
    }

    /**
     * 把一行上游输出转发为 SSE 事件。
     *
     * <p>写入失败说明前端已断开，此时抛异常中止读取，交由外层关闭上游连接。
     *
     * @param emitter  SSE 发射器
     * @param tenantId 租户标识，仅用于日志定位
     * @param line     已解析的上游行
     */
    private void forwardLine(SseEmitter emitter, String tenantId, RagStreamLine line) {
        // 参考片段可能随缓存命中的合并行一起到达，先发 references 再发正文
        if (!line.references().isEmpty()) {
            sendEvent(emitter, RagSseEventNames.REFERENCES,
                    RagSsePayloads.references(line.references()));
        }
        switch (line.kind()) {
            case CONTENT -> sendEvent(emitter, RagSseEventNames.DELTA, line.content());
            case ERROR -> {
                // 上游原因只写日志（可能含内部服务名），不回传前端
                logger.warn("[知识库流式问答] 上游返回错误 tenant: {}, reason: {}",
                        tenantId, line.error());
                // 直接中断读取：继续消费只会拿到空行，并且会重复 complete
                throw new ServiceException(RagErrorCodeConstants.UPSTREAM_ERROR);
            }
            default -> {
                // REFERENCES 已在上面统一下发，进度与忽略行不透传
            }
        }
    }

    /**
     * 发送 error 事件后结束连接，发送失败也要保证 emitter 进入终态。
     *
     * <p>SSE 已经发出响应头，无法再改 HTTP 状态码，因此错误只能走事件通道；
     * 结束前必须调用 {@code complete}，否则前端会一直等待到超时。
     * error 事件本身也可能因前端已断开而发不出去，此时仍需 complete。
     *
     * @param emitter       SSE 发射器
     * @param businessError 业务异常
     * @param tenantId      租户标识，仅用于日志定位
     */
    private void closeAfterError(SseEmitter emitter, ServiceException businessError,
                                 String tenantId) {
        try {
            emitter.send(SseEmitter.event()
                    .name(RagSseEventNames.ERROR)
                    .data(RagSsePayloads.error(
                            businessError.getErrorCode().code(),
                            businessError.getErrorCode().message(),
                            businessError.getErrorCode().userTip())));
        } catch (IOException | IllegalStateException sendFailure) {
            // 前端已断开或 emitter 已终态：错误事件发不出去是预期结果，不影响收尾
            logger.debug("[知识库流式问答] error 事件发送失败 tenant: {}, 原因: {}",
                    tenantId, sendFailure.getMessage());
        } finally {
            emitter.complete();
        }
    }

    /**
     * 发送一个 SSE 事件。
     *
     * <p>只吞「emitter 已进入终态」这类异常（重复发送无意义）；
     * 写入 IO 失败说明前端已断开，必须<b>上抛</b>——否则读取线程会继续把
     * 整条上游流读完再丢弃，白占带宽与模型配额。
     *
     * @param emitter   SSE 发射器
     * @param eventName 事件名
     * @param data      事件数据
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IllegalStateException alreadyCompleted) {
            // emitter 已 completed / 超时，后续写入必然失败且无意义
            logger.debug("[知识库流式问答] 事件 {} 已无法发送: {}",
                    eventName, alreadyCompleted.getMessage());
        } catch (IOException clientGone) {
            // 前端断开：抛出以中止读取循环，由 runStream 统一收尾关闭上游连接
            throw new ServiceException(RagErrorCodeConstants.STREAM_START_FAILED,
                    "客户端连接已断开");
        }
    }
}
