package com.badminton.mes.module.rag.config;

import com.badminton.mes.module.rag.client.RagClient;
import com.badminton.mes.module.rag.client.RagStreamClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

/** Verify production constructor selection in an actual Spring context. */
class RagClientWiringTest {
    @Test
    void bothClientsCanBeConstructedDespiteAdditionalTestConstructors() {
        new ApplicationContextRunner().withBean(RagProperties.class)
                .withBean(tools.jackson.databind.json.JsonMapper.class, tools.jackson.databind.json.JsonMapper::new)
                .withUserConfiguration(RagClient.class, RagStreamClient.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RagClient.class);
                    assertThat(context).hasSingleBean(RagStreamClient.class);
                });
    }
}
