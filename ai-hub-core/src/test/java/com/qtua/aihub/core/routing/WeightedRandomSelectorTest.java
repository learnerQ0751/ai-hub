package com.qtua.aihub.core.routing;

import com.qtua.aihub.common.catalog.ModelDeployment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedRandomSelectorTest {

    private final WeightedRandomSelector selector = new WeightedRandomSelector(new Random(42));

    @Test
    void always_selects_single_deployment() {
        ModelDeployment only = deployment("deepseek-chat", 1);

        for (int i = 0; i < 20; i++) {
            assertThat(selector.select("chat", List.of(only))).isSameAs(only);
        }
    }

    @Test
    void distributes_roughly_evenly_when_weights_are_equal() {
        ModelDeployment first = deployment("first", 1);
        ModelDeployment second = deployment("second", 1);

        List<ModelDeployment> selected = select("chat", List.of(first, second), 10_000);

        assertThat(count(selected, first)).isBetween(4_500L, 5_500L);
    }

    @Test
    void respects_weight_ratio() {
        ModelDeployment heavy = deployment("heavy", 3);
        ModelDeployment light = deployment("light", 1);

        List<ModelDeployment> selected = select("chat", List.of(heavy, light), 10_000);

        assertThat(count(selected, heavy)).isBetween(7_000L, 8_000L);
    }

    @Test
    void falls_back_to_one_for_non_positive_weights() {
        ModelDeployment invalid = deployment("invalid", 0);
        ModelDeployment valid = deployment("valid", 100);

        List<ModelDeployment> selected = select("chat", List.of(invalid, valid), 10_000);

        assertThat(count(selected, valid)).isGreaterThan(9_500L);
    }

    private List<ModelDeployment> select(String alias, List<ModelDeployment> deployments, int times) {
        List<ModelDeployment> selected = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            selected.add(selector.select(alias, deployments));
        }
        return selected;
    }

    private long count(List<ModelDeployment> selected, ModelDeployment deployment) {
        return selected.stream().filter(item -> item == deployment).count();
    }

    private ModelDeployment deployment(String model, int weight) {
        return new ModelDeployment("chat", "https://example.com", "test-key", model, weight, 0);
    }
}
