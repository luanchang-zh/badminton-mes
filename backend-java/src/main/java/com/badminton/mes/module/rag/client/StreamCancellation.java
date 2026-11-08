package com.badminton.mes.module.rag.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 流式调用的取消信号。
 *
 * <p>前端断开 SSE 连接时，唯一的资源是上游那条 HTTP 连接：只有把它关掉，
 * 被阻塞的读取才会立即抛错返回，模型侧的生成也会随之停止。
 * 关闭动作必须允许「尚未建立连接就取消」，因此句柄以 {@link AtomicReference} 存放，
 * {@link #bind} 时若已取消则立刻关闭，避免连接泄漏。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
public final class StreamCancellation {

    private static final Logger logger = LoggerFactory.getLogger(StreamCancellation.class);

    /** 已取消标记 */
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /** 当前打开的上游流句柄，尚未建立时为 null */
    private final AtomicReference<Closeable> openStream = new AtomicReference<>();

    /**
     * 绑定刚建立的上游流句柄。
     *
     * @param stream 上游流句柄
     */
    public void bind(Closeable stream) {
        openStream.set(stream);
        // 取消可能发生在建连之前：此时 bind 到的是已废弃的连接，必须立刻关闭
        if (cancelled.get()) {
            closeQuietly(stream);
        }
    }

    /**
     * 是否已取消。
     *
     * @return 已取消为 true
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 发出取消信号并关闭上游连接。
     *
     * <p>幂等：重复调用不会重复关闭，可以安全地同时挂在
     * {@code onCompletion}、{@code onTimeout}、{@code onError} 三个回调上。
     */
    public void cancel() {
        cancelled.set(true);
        closeQuietly(openStream.getAndSet(null));
    }

    /**
     * 关闭句柄，忽略关闭过程中的异常。
     *
     * @param stream 待关闭句柄，可为 null
     */
    private static void closeQuietly(Closeable stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException closeError) {
            // 关闭失败不影响取消语义：读取侧会因为流被放弃而返回
            logger.debug("Failed to close upstream query stream: {}", closeError.getMessage());
        }
    }
}
