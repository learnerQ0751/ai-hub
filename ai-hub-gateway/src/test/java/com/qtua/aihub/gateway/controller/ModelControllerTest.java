package com.qtua.aihub.gateway.controller;

import com.qtua.aihub.start.error.GatewayExceptionHandler;
import com.qtua.aihub.gateway.filter.AuthFilter;
import com.qtua.aihub.start.filter.RequestIdFilter;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.service.gateway.GatewayGovernanceService;
import com.qtua.aihub.start.config.JacksonConfig;
import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.api.ModelService;
import com.qtua.aihub.api.model.res.ModelListResDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ModelController.class)
@Import({RequestIdFilter.class, AuthFilter.class, GatewayExceptionHandler.class, JacksonConfig.class})
class ModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModelService modelService;

    @MockitoBean
    private KeyService keyService;

    @MockitoBean
    private GatewayProperties gatewayProperties;

    @MockitoBean
    private GatewayGovernanceService gatewayGovernanceService;

    @Test
    void lists_models() throws Exception {
        when(modelService.listModels()).thenReturn(ModelListResDTO.of(List.of("chat", "reasoner")));

        mockMvc.perform(get("/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("list"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("chat"))
                .andExpect(jsonPath("$.data[0].object").value("model"))
                .andExpect(jsonPath("$.data[0].owned_by").value("ai-hub"));
    }

    @Test
    void supports_v1_prefix() throws Exception {
        when(modelService.listModels()).thenReturn(ModelListResDTO.of(List.of("chat")));

        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("chat"));
    }

    @Test
    void does_not_expose_upstream_details() throws Exception {
        when(modelService.listModels()).thenReturn(ModelListResDTO.of(List.of("chat")));

        MvcResult result = mockMvc.perform(get("/models")).andReturn();
        String body = result.getResponse().getContentAsString();

        assertThat(body).doesNotContain("deepseek", "api-base", "api-key", "rpm", "tpm");
    }

    @Test
    void requires_bearer_token_when_master_key_is_configured() throws Exception {
        when(gatewayProperties.getMasterKey()).thenReturn("secret-master");
        when(modelService.listModels()).thenReturn(ModelListResDTO.of(List.of("chat")));

        mockMvc.perform(get("/models"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.type").value("auth_error"));
    }
}
