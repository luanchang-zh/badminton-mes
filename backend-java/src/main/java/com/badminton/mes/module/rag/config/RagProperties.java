package com.badminton.mes.module.rag.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 知识库上游配置，对应 application.yml 的 {@code mes.rag} 段。
 *
 * <p>默认地址指向本机回环，端口与 P2 的服务默认端口一致。
 * 内部密钥只从配置读取，不写进任何日志或异常信息。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@ConfigurationProperties(prefix = "mes.rag")
public class RagProperties {

    /** 上游服务基础地址，默认本机回环 */
    private String baseUrl = "http://127.0.0.1:9621";

    /** 内部访问密钥，对应上游的 X-API-Key；为空表示未配置 */
    private String internalApiKey = "";

    /** 建立连接超时 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 读取响应超时；索引与查询可能较慢，默认给足 */
    private Duration readTimeout = Duration.ofSeconds(60);

    /** 单次上传允许的最大字节数，默认 20MB */
    private long maxUploadSize = 20L * 1024 * 1024;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public long getMaxUploadSize() {
        return maxUploadSize;
    }

    public void setMaxUploadSize(long maxUploadSize) {
        this.maxUploadSize = maxUploadSize;
    }
}
