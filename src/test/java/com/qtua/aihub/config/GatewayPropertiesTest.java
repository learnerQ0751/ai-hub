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

    @Test
    void binds_deployment_weights() {
        runner()
                .withPropertyValues(deploymentProperties("chat", 0))
                .withPropertyValues("gateway.model-list.chat[0].rpm=600")
                .run(context -> {
                    GatewayProperties properties = context.getBean(GatewayProperties.class);
                    GatewayProperties.Deployment deployment =
                            properties.getModelList().get("chat").get(0);
                    assertThat(deployment.getRpm()).isEqualTo(600);
                    assertThat(deployment.getTpm()).isNull();
                });
    }

    @Test
    void starts_when_all_deployments_use_rpm() {
        runner()
                .withPropertyValues(deploymentProperties("chat", 0))
                .withPropertyValues(deploymentProperties("chat", 1))
                .withPropertyValues(
                        "gateway.model-list.chat[0].rpm=600",
                        "gateway.model-list.chat[1].rpm=200")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void starts_when_all_deployments_use_tpm() {
        runner()
                .withPropertyValues(deploymentProperties("chat", 0))
                .withPropertyValues(deploymentProperties("chat", 1))
                .withPropertyValues(
                        "gateway.model-list.chat[0].tpm=60000",
                        "gateway.model-list.chat[1].tpm=20000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    GatewayProperties properties = context.getBean(GatewayProperties.class);
                    assertThat(properties.getModelList().get("chat").get(0).getTpm()).isEqualTo(60000);
                });
    }

    @Test
    void starts_when_alias_has_no_weights() {
        runner()
                .withPropertyValues(deploymentProperties("chat", 0))
                .withPropertyValues(deploymentProperties("chat", 1))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void fails_when_alias_mixes_rpm_and_tpm() {
        runner()
                .withPropertyValues(deploymentProperties("chat", 0))
                .withPropertyValues(deploymentProperties("chat", 1))
                .withPropertyValues(
                        "gateway.model-list.chat[0].rpm=600",
                        "gateway.model-list.chat[1].tpm=20000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void fails_when_alias_partially_configures_weights() {
        runner()
                .withPropertyValues(deploymentProperties("chat", 0))
                .withPropertyValues(deploymentProperties("chat", 1))
                .withPropertyValues("gateway.model-list.chat[0].rpm=600")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(TestConfig.class);
    }

    private String[] deploymentProperties(String alias, int index) {
        String prefix = "gateway.model-list." + alias + "[" + index + "].";
        return new String[] {
                prefix + "api-base=https://example.com",
                prefix + "api-key=key-" + index,
                prefix + "model=deepseek-chat"
        };
    }

    @Configuration
    @EnableConfigurationProperties(GatewayProperties.class)
    static class TestConfig {
    }
}
