package com.badminton.mes.module.rag.client;

import java.time.Duration;

import com.badminton.mes.module.rag.config.RagProperties;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 上游 HTTP 客户端工厂。
 *
 * <p>同步调用与流式调用共用连接超时，但读取超时不同：同步调用按
 * {@code read-timeout} 快速失败，流式调用必须等模型逐字输出，不能沿用同一个值。
 * 因此这里把读取超时作为参数而不是内部固定值。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
final class RagRestClientFactory {

    private RagRestClientFactory() {
    }

    /**
     * 构建带超时配置的客户端。
     *
     * @param properties  上游配置
     * @param readTimeout 读取超时
     * @return HTTP 客户端
     */
    static RestClient build(RagProperties properties, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}
