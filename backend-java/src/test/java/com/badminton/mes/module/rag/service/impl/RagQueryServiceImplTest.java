package com.badminton.mes.module.rag.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
import com.badminton.mes.module.rag.service.RagTenantResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 问答 Service 测试。
 *
 * <p>最关键的断言是「<b>流式问答在请求线程解析租户</b>」：登录上下文是 ThreadLocal，
 * 若改成在读线程里解析，线上会稳定报登录失效。测试用一个同步执行器把
 * 读线程等同于当前线程无法区分，因此改用「解析失败应立即抛出」来证明解析确实发生在返回前。
 */
@ExtendWith(MockitoExtension.class)
class RagQueryServiceImplTest {

    /** 解析器返回的租户 */
    private static final String TENANT = "ws_002";

    @Mock
    private RagTenantResolver tenantResolver;

    @Mock
    private RagClient ragClient;

    @Mock
    private RagStreamClient ragStreamClient;

    /** 同步执行器：把读线程等同于当前线程，便于断言 */
    private final ExecutorService directExecutor = new DirectExecutor();

    private RagQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RagQueryServiceImpl(tenantResolver, ragClient, ragStreamClient,
                directExecutor, new RagProperties());
    }

    @Test
    @DisplayName("同步问答用解析出的租户与模式取值调用客户端")
    void shouldQueryWithResolvedTenantAndModeValue() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        RagQueryResult expected = new RagQueryResult("答案", List.of("片段"), "mix");
        when(ragClient.query(TENANT, "问题", "mix")).thenReturn(expected);

        assertThat(service.query("问题", RagQueryMode.MIX)).isEqualTo(expected);
        verify(ragClient).query(TENANT, "问题", "mix");
    }

    @Test
    @DisplayName("同步问答未指定模式时按缺省模式调用")
    void shouldQueryWithDefaultMode() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        when(ragClient.query(eq(TENANT), eq("问题"), eq("naive")))
                .thenReturn(new RagQueryResult("答案", List.of(), "naive"));

        service.query("问题", null);

        verify(ragClient).query(TENANT, "问题", "naive");
    }

    @Test
    @DisplayName("流式问答在返回前解析租户：解析失败立即抛出而不是静默建立连接")
    void streamQueryShouldResolveTenantBeforeReturning() {
        when(tenantResolver.resolveTenantId())
                .thenThrow(new ServiceException(RagErrorCodeConstants.TENANT_NOT_BOUND));

        assertThatThrownBy(() -> service.streamQuery("问题", RagQueryMode.MIX))
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.TENANT_NOT_BOUND);

        // 解析失败就不应再去连上游
        verifyNoInteractions(ragStreamClient);
    }

    @Test
    @DisplayName("流式问答返回可用的 SSE 发射器并转发正文")
    void streamQueryShouldForwardContent() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        when(ragStreamClient.streamQuery(eq(TENANT), eq("问题"), eq(RagQueryMode.MIX),
                any(), any())).thenAnswer(invocation -> {
                    Consumer<RagStreamLine> consumer = invocation.getArgument(3);
                    consumer.accept(RagStreamLine.content("回答"));
                    return new RagQueryResult("回答", List.of(), "mix");
                });

        SseEmitter emitter = service.streamQuery("问题", RagQueryMode.MIX);

        assertThat(emitter).isNotNull();
        verify(ragStreamClient).streamQuery(eq(TENANT), eq("问题"), eq(RagQueryMode.MIX),
                any(), any());
    }

    @Test
    @DisplayName("流式问答遇到上游错误时发 error 事件而不是抛给调用方")
    void streamQueryShouldSendErrorEvent() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        when(ragStreamClient.streamQuery(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new ServiceException(RagErrorCodeConstants.UPSTREAM_UNAVAILABLE));

        // 不应抛出：SSE 已建立，错误只能走事件通道
        assertThat(service.streamQuery("问题", RagQueryMode.MIX)).isNotNull();
    }

    @Test
    @DisplayName("图谱标签查询用解析出的租户")
    void shouldListLabelsWithResolvedTenant() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        when(ragClient.listGraphLabels(TENANT)).thenReturn(List.of("设备", "工序"));

        assertThat(service.listGraphLabels()).containsExactly("设备", "工序");
    }

    @Test
    @DisplayName("子图查询：深度被夹在 1~3，超界值不会透传给上游")
    void shouldClampGraphDepth() {
        when(tenantResolver.resolveTenantId()).thenReturn(TENANT);
        when(ragClient.getGraph(eq(TENANT), eq("设备"), anyInt()))
                .thenReturn(new RagGraphResult(List.of(), List.of()));

        service.getGraph("设备", 0);
        service.getGraph("设备", 2);
        service.getGraph("设备", 99);

        // 上游只校验 ge=1，没有上界；上界必须由本系统保证，否则一次请求可拉出上千节点
        verify(ragClient).getGraph(TENANT, "设备", 1);
        verify(ragClient).getGraph(TENANT, "设备", 2);
        verify(ragClient).getGraph(TENANT, "设备", 3);
        verify(ragClient, never()).getGraph(eq(TENANT), eq("设备"), eq(0));
        verify(ragClient, never()).getGraph(eq(TENANT), eq("设备"), eq(99));
    }

    /**
     * 在当前线程直接执行任务的执行器。
     *
     * <p>用它替代真实线程池，测试才能同步断言「任务已执行完毕」，
     * 不必引入 sleep 或轮询这类不稳定手段。
     */
    private static final class DirectExecutor extends AbstractExecutorService {

        private volatile boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
