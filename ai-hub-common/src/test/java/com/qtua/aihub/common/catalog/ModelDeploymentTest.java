package com.qtua.aihub.common.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelDeploymentTest {

    @Test
    void toString_does_not_leak_api_key() {
        ModelDeployment deployment = new ModelDeployment(
                "chat", "https://example.com", "super-secret-key", "deepseek-chat", 3, 1);

        assertThat(deployment.toString())
                .contains("chat")
                .contains("deepseek-chat")
                .contains("priority=1")
                .doesNotContain("super-secret-key");
    }

    @Test
    void carries_priority_for_primary_backup_ordering() {
        ModelDeployment deployment = new ModelDeployment(
                "chat", "https://example.com", "key", "deepseek-chat", 3, 2);

        assertThat(deployment.priority()).isEqualTo(2);
    }
}
