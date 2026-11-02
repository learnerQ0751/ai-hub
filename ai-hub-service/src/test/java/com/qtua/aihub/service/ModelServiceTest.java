package com.qtua.aihub.service;

import com.qtua.aihub.api.model.res.ModelListResDTO;
import com.qtua.aihub.common.catalog.ModelCatalog;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelServiceTest {

    @Test
    void lists_aliases_in_configuration_order() {
        ModelCatalog modelCatalog = mock(ModelCatalog.class);
        when(modelCatalog.listAliases()).thenReturn(
                new LinkedHashSet<>(java.util.List.of("chat", "reasoner")));

        ModelListResDTO response = new ModelServiceImpl(modelCatalog).listModels();

        assertThat(response.data())
                .extracting(ModelListResDTO.ModelInfo::id)
                .containsExactly("chat", "reasoner");
    }

    @Test
    void exposes_only_aliases_not_deployment_details() {
        ModelCatalog modelCatalog = mock(ModelCatalog.class);
        when(modelCatalog.listAliases()).thenReturn(new LinkedHashSet<>(java.util.List.of("chat")));

        ModelListResDTO response = new ModelServiceImpl(modelCatalog).listModels();

        assertThat(response.data())
                .extracting(ModelListResDTO.ModelInfo::id)
                .containsExactly("chat");
    }
}
