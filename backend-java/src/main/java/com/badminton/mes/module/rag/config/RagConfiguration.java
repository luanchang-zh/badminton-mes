package com.badminton.mes.module.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
}
