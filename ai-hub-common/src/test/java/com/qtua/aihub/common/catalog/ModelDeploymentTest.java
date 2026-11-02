package com.qtua.aihub.common.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelDeploymentTest {

    @Test
    void toString_does_not_leak_api_key() {
        ModelDeployment deployment = new ModelDeployment(
                "chat", "https://example.com", "super-secret-key", "deepseek-chat", 3);

        assertThat(deployment.toString())
                .contains("chat")
                .contains("deepseek-chat")
                .doesNotContain("super-secret-key");
    }
}
