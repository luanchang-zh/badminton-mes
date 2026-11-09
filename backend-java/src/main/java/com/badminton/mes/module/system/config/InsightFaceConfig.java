package com.badminton.mes.module.system.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 {@link InsightFaceProperties}。项目没有开启 {@code @ConfigurationPropertiesScan}，需要显式声明。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
@Configuration
@EnableConfigurationProperties(InsightFaceProperties.class)
public class InsightFaceConfig {
}
