package com.badminton.mes.module.rag.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 显式注册 RAG 模块的配置属性。
 *
 * <p>项目其余模块用 {@code @Value} 逐项注入，但 RAG 的超时是 {@code Duration}、
 * 并有多项强相关配置，用类型安全的 ConfigurationProperties 更易校验。
 * 这里集中注册，不改动主启动类，模块可独立启用或移除。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RagConfiguration.class);

    @Autowired
    private RagProperties properties;

    /**
     * 校验超时配置之间的关系，配反了直接拒绝启动。
     *
     * <p>SSE 存活时间必须长于流式读取超时：若配反，容器会先掐断连接，
     * 表现为「问答一半就断」，而且从日志里很难看出是配置问题而不是上游问题。
     * 这类关系用注释约定不住，只能在启动时硬校验。
     *
     * @throws IllegalStateException 超时关系不合法
     */
    @PostConstruct
    void validateTimeoutRelation() {
        if (properties.getSseTimeout().compareTo(properties.getStreamReadTimeout()) <= 0) {
            throw new IllegalStateException(
                    "mes.rag.sse-timeout 必须大于 mes.rag.stream-read-timeout，"
                            + "否则流式问答会在生成过程中被容器提前断开");
        }
        logger.debug("[RAG 配置] 流式读取超时 {}，SSE 超时 {}",
                properties.getStreamReadTimeout(), properties.getSseTimeout());
    }

    /**
     * 流式问答专用的执行器。
     *
     * <p>用虚拟线程而不是固定线程池：每次流式问答都会阻塞在一个 HTTP 读取上，
     * 耗时可达数分钟。固定线程池在这种情况下会被占满，此次连接数一多就会排队；
     * 虚拟线程的阻塞几乎不占平台线程，天然适配这种「大量长阻塞」场景。
     *
     * <p>用 {@code destroyMethod} 关闭执行器，避免应用停止时残留读取线程。
     *
     * @return 虚拟线程执行器
     */
    @Bean(name = "ragStreamExecutor", destroyMethod = "shutdownNow")
    public ExecutorService ragStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
