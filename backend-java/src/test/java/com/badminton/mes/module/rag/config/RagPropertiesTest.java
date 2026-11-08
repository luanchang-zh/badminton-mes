package com.badminton.mes.module.rag.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RAG 配置默认值测试。
 *
 * <p>默认值决定「忘配配置时会发生什么」：地址回环、超时给足、上传有上限。
 * 这些默认值若被无意改动，会直接表现为连不上或大文件被静默丢弃。
 */
class RagPropertiesTest {

    @Test
    @DisplayName("未配置时使用回环地址与合理默认值")
    void shouldProvideSafeDefaults() {
        RagProperties properties = new RagProperties();

        assertThat(properties.getBaseUrl()).isEqualTo("http://127.0.0.1:9621");
        assertThat(properties.getInternalApiKey()).isEmpty();
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getMaxUploadSize()).isEqualTo(20L * 1024 * 1024);
    }

    @Test
    @DisplayName("可以覆盖各项配置")
    void shouldAllowOverrides() {
        RagProperties properties = new RagProperties();
        properties.setBaseUrl("http://rag.internal:9000");
        properties.setInternalApiKey("secret");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(120));
        properties.setMaxUploadSize(1024L);

        assertThat(properties.getBaseUrl()).isEqualTo("http://rag.internal:9000");
        assertThat(properties.getInternalApiKey()).isEqualTo("secret");
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.getMaxUploadSize()).isEqualTo(1024L);
    }
}
