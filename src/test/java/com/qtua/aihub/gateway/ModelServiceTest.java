package com.qtua.aihub.gateway;

import com.qtua.aihub.config.GatewayProperties;
import com.qtua.aihub.config.GatewayProperties.Deployment;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelServiceTest {

    @Test
    void lists_aliases_in_configuration_order() {
        GatewayProperties properties = mock(GatewayProperties.class);
        Map<String, List<Deployment>> modelList = new LinkedHashMap<>();
        modelList.put("chat", List.of(deployment("deepseek-chat")));
        modelList.put("reasoner", List.of(deployment("deepseek-reasoner")));
        when(properties.getModelList()).thenReturn(modelList);

        ModelListResponse response = new ModelService(properties).listModels();

        assertThat(response.object()).isEqualTo("list");
        assertThat(response.data())
                .extracting(ModelListResponse.ModelInfo::id)
                .containsExactly("chat", "reasoner");
        assertThat(response.data()).allSatisfy(info -> {
            assertThat(info.object()).isEqualTo("model");
            assertThat(info.created()).isZero();
            assertThat(info.ownedBy()).isEqualTo("ai-hub");
        });
    }

    @Test
    void exposes_only_aliases_not_deployment_details() {
        GatewayProperties properties = mock(GatewayProperties.class);
        Map<String, List<Deployment>> modelList = Map.of("chat", List.of(deployment("deepseek-chat")));
        when(properties.getModelList()).thenReturn(modelList);

        ModelListResponse response = new ModelService(properties).listModels();

        assertThat(response.data()).extracting(ModelListResponse.ModelInfo::id)
                .containsExactly("chat");
    }

    private Deployment deployment(String model) {
        Deployment deployment = new Deployment();
        deployment.setApiBase("https://example.com");
        deployment.setApiKey("test-key");
        deployment.setModel(model);
        return deployment;
    }
}
