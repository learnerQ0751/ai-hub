package com.qtua.aihub.manager.catalog;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlModelCatalogTest {

    @Test
    void maps_aliases_and_deployments() {
        GatewayProperties properties = properties(
                "chat", List.of(deployment("deepseek-chat", 3, null)),
                "reasoner", List.of(deployment("deepseek-reasoner", null, null)));

        YamlModelCatalog catalog = new YamlModelCatalog(properties);

        assertThat(catalog.listAliases()).containsExactly("chat", "reasoner");
        assertThat(catalog.findDeployments("chat"))
                .extracting(ModelDeployment::upstreamModel)
                .containsExactly("deepseek-chat");
        assertThat(catalog.findDeployments("reasoner").get(0).weight()).isEqualTo(1);
    }

    @Test
    void unknown_alias_returns_empty_list() {
        YamlModelCatalog catalog = new YamlModelCatalog(properties());

        assertThat(catalog.findDeployments("no-such-model")).isEmpty();
    }

    @Test
    void weight_prefers_rpm_then_tpm_then_default() {
        GatewayProperties properties = properties(
                "chat", List.of(
                        deployment("a", 3, 100),
                        deployment("b", null, 5),
                        deployment("c", null, null),
                        deployment("d", 0, null)));

        YamlModelCatalog catalog = new YamlModelCatalog(properties);

        assertThat(catalog.findDeployments("chat"))
                .extracting(ModelDeployment::weight)
                .containsExactly(3, 5, 1, 1);
    }

    @Test
    void returns_immutable_collections() {
        YamlModelCatalog catalog = new YamlModelCatalog(
                properties("chat", List.of(deployment("deepseek-chat", null, null))));

        assertThatThrownBy(() -> catalog.findDeployments("chat").add(
                new ModelDeployment("chat", "x", "y", "z", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.listAliases().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private GatewayProperties properties(Object... entries) {
        GatewayProperties properties = new GatewayProperties();
        Map<String, List<GatewayProperties.Deployment>> modelList = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            String alias = (String) entries[i];
            @SuppressWarnings("unchecked")
            List<GatewayProperties.Deployment> deployments = (List<GatewayProperties.Deployment>) entries[i + 1];
            modelList.put(alias, deployments);
        }
        properties.setModelList(modelList);
        return properties;
    }

    private GatewayProperties.Deployment deployment(String model, Integer rpm, Integer tpm) {
        GatewayProperties.Deployment deployment = new GatewayProperties.Deployment();
        deployment.setApiBase("https://example.com");
        deployment.setApiKey("test-key");
        deployment.setModel(model);
        deployment.setRpm(rpm);
        deployment.setTpm(tpm);
        return deployment;
    }
}
