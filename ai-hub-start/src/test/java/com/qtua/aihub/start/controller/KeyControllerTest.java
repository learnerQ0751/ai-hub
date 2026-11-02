package com.qtua.aihub.start.controller;

import com.qtua.aihub.AiHubApplication;
import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.KeyGenerateReqDTO;
import com.qtua.aihub.api.model.res.KeyBlockResDTO;
import com.qtua.aihub.api.model.res.KeyGenerateResDTO;
import com.qtua.aihub.api.model.res.KeyInfoResDTO;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.start.config.JacksonConfig;
import com.qtua.aihub.start.error.GatewayExceptionHandler;
import com.qtua.aihub.start.filter.AuthFilter;
import com.qtua.aihub.start.filter.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KeyController.class)
@ContextConfiguration(classes = AiHubApplication.class)
@Import({RequestIdFilter.class, AuthFilter.class, GatewayExceptionHandler.class, JacksonConfig.class})
class KeyControllerTest {

    private static final String MASTER_KEY = "master-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KeyService keyService;

    @MockitoBean
    private GatewayProperties gatewayProperties;

    @BeforeEach
    void setUp() {
        when(gatewayProperties.getMasterKey()).thenReturn(MASTER_KEY);
    }

    @Test
    void generates_key_with_master_token_and_binds_snake_case_fields() throws Exception {
        KeyGenerateResDTO response = new KeyGenerateResDTO("sk-abc", List.of("chat"),
                BigDecimal.ZERO, Map.of("user", "tester"), "2026-10-22T10:00:00Z",
                new BigDecimal("5.0"), 60, 1000, false);
        when(keyService.generate(any(KeyGenerateReqDTO.class))).thenReturn(response);

        mockMvc.perform(post("/key/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"models\":[\"chat\"],\"duration\":\"1h\","
                                + "\"metadata\":{\"user\":\"tester\"},"
                                + "\"max_budget\":5.0,\"rpm_limit\":60,\"tpm_limit\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("sk-abc"))
                .andExpect(jsonPath("$.max_budget").value(5.0))
                .andExpect(jsonPath("$.rpm_limit").value(60));

        ArgumentCaptor<KeyGenerateReqDTO> captor = ArgumentCaptor.forClass(KeyGenerateReqDTO.class);
        verify(keyService).generate(captor.capture());
        KeyGenerateReqDTO request = captor.getValue();
        assertThat(request.models()).containsExactly("chat");
        assertThat(request.duration()).isEqualTo("1h");
        assertThat(request.maxBudget()).isEqualByComparingTo("5.0");
        assertThat(request.rpmLimit()).isEqualTo(60);
        assertThat(request.tpmLimit()).isEqualTo(1000);
    }

    @Test
    void rejects_virtual_key_on_master_only_endpoint() throws Exception {
        when(keyService.authenticate("sk-virtual"))
                .thenReturn(KeyAuthDTO.virtualKey("hash", List.of("chat")));

        mockMvc.perform(post("/key/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer sk-virtual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"models\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.type").value("auth_error"));
    }

    @Test
    void rejects_missing_token() throws Exception {
        mockMvc.perform(post("/key/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"models\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.type").value("auth_error"));
    }

    @Test
    void returns_key_info() throws Exception {
        KeyInfoResDTO.KeyInfo info = new KeyInfoResDTO.KeyInfo(BigDecimal.ZERO, BigDecimal.ZERO,
                List.of("chat"), null, null, null, null, false, "2026-10-22T00:00:00Z",
                Map.of("user", "tester"), null);
        when(keyService.info("sk-abc")).thenReturn(new KeyInfoResDTO("sk-****-abc", info));

        mockMvc.perform(get("/key/info").param("key", "sk-abc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("sk-****-abc"))
                .andExpect(jsonPath("$.info.spend").value(0))
                .andExpect(jsonPath("$.info.models[0]").value("chat"));
    }

    @Test
    void blocks_key() throws Exception {
        when(keyService.block("sk-abc")).thenReturn(new KeyBlockResDTO("sk-****-abc", true));

        mockMvc.perform(post("/key/block")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"sk-abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));
    }
}
