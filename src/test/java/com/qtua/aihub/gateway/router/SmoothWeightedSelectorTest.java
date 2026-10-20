package com.qtua.aihub.gateway.router;

import com.qtua.aihub.config.GatewayProperties.Deployment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmoothWeightedSelectorTest {

    private final SmoothWeightedSelector selector = new SmoothWeightedSelector();

    @Test
    void always_selects_single_deployment() {
        Deployment only = deployment("deepseek-chat");

        for (int i = 0; i < 20; i++) {
            assertThat(selector.select("chat", List.of(only))).isSameAs(only);
        }
    }

    @Test
    void distributes_evenly_when_weights_are_equal() {
        Deployment first = deployment("first");
        Deployment second = deployment("second");

        List<Deployment> selected = select("chat", List.of(first, second), 100);

        assertThat(count(selected, first)).isEqualTo(50);
        assertThat(count(selected, second)).isEqualTo(50);
    }

    @Test
    void respects_weight_ratio() {
        Deployment heavy = deployment("heavy");
        heavy.setRpm(3);
        Deployment light = deployment("light");
        light.setRpm(1);

        List<Deployment> selected = select("chat", List.of(heavy, light), 100);

        assertThat(count(selected, heavy)).isEqualTo(75);
        assertThat(count(selected, light)).isEqualTo(25);
    }

    @Test
    void interleaves_equal_weights() {
        Deployment first = deployment("first");
        Deployment second = deployment("second");

        List<Deployment> selected = select("chat", List.of(first, second), 4);

        assertThat(selected).containsExactly(first, second, first, second);
    }

    @Test
    void defaults_missing_weights_to_one() {
        Deployment first = deployment("first");
        Deployment second = deployment("second");
        Deployment third = deployment("third");

        List<Deployment> selected = select("chat", List.of(first, second, third), 30);

        assertThat(count(selected, first)).isEqualTo(10);
        assertThat(count(selected, second)).isEqualTo(10);
        assertThat(count(selected, third)).isEqualTo(10);
    }

    @Test
    void falls_back_to_one_for_non_positive_weights() {
        Deployment invalid = deployment("invalid");
        invalid.setRpm(0);
        Deployment valid = deployment("valid");
        valid.setRpm(100);

        List<Deployment> selected = select("chat", List.of(invalid, valid), 101);

        assertThat(count(selected, invalid)).isEqualTo(1);
        assertThat(count(selected, valid)).isEqualTo(100);
    }

    @Test
    void uses_tpm_when_rpm_is_absent() {
        Deployment first = deployment("first");
        first.setTpm(3);
        Deployment second = deployment("second");
        second.setTpm(1);

        List<Deployment> selected = select("chat", List.of(first, second), 100);

        assertThat(count(selected, first)).isEqualTo(75);
        assertThat(count(selected, second)).isEqualTo(25);
    }

    private List<Deployment> select(String alias, List<Deployment> deployments, int times) {
        List<Deployment> selected = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            selected.add(selector.select(alias, deployments));
        }
        return selected;
    }

    private long count(List<Deployment> selected, Deployment deployment) {
        return selected.stream().filter(item -> item == deployment).count();
    }

    private Deployment deployment(String model) {
        Deployment deployment = new Deployment();
        deployment.setApiBase("https://example.com");
        deployment.setApiKey("test-key");
        deployment.setModel(model);
        return deployment;
    }
}
