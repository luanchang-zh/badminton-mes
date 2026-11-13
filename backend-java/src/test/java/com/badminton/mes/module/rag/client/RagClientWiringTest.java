package com.badminton.mes.module.rag.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badminton.mes.module.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** 启动真实客户端 Bean，避免 HTTP 测试中的上游替身掩盖构造器注入问题。 */
class RagClientWiringTest {
    @Test
    void productionClientsCanStartWithoutAnUpstreamService() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RagProperties.class, RagProperties::new);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(RagClient.class, RagStreamClient.class);
            context.refresh();
            assertThat(context.getBean(RagClient.class)).isNotNull();
            assertThat(context.getBean(RagStreamClient.class)).isNotNull();
        }
    }
}
