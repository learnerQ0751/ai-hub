package com.qtua.aihub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "gateway.master-key=test-master",
                    "gateway.model-list.chat-default[0].api-base=https://example.com",
                    "gateway.model-list.chat-default[0].api-key=test-key",
                    "gateway.model-list.chat-default[0].model=deepseek-chat");

    @Test
    void binds_model_list() {
        runner.run(context -> {
            GatewayProperties properties = context.getBean(GatewayProperties.class);
            assertThat(properties.getMasterKey()).isEqualTo("test-master");
            assertThat(properties.getModelList()).containsKey("chat-default");

            GatewayProperties.Deployment deployment =
                    properties.getModelList().get("chat-default").get(0);
            assertThat(deployment.getApiBase()).isEqualTo("https://example.com");
            assertThat(deployment.getApiKey()).isEqualTo("test-key");
            assertThat(deployment.getModel()).isEqualTo("deepseek-chat");
        });
    }

    @Configuration
    @EnableConfigurationProperties(GatewayProperties.class)
    static class TestConfig {
    }
}
