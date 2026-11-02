package com.qtua.aihub.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.ApiKeyMapper;
import com.qtua.aihub.dal.model.ApiKeyDO;
import com.qtua.aihub.manager.model.ApiKeyBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyManagerTest {

    private static final String KEY_HASH = "hash";
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-22T00:00:00Z");

    private ApiKeyMapper apiKeyMapper;
    private ApiKeyManager manager;

    @BeforeEach
    void setUp() {
        apiKeyMapper = mock(ApiKeyMapper.class);
        manager = new ApiKeyManager(apiKeyMapper, new ObjectMapper());
    }

    @Test
    void save_maps_bo_to_do_with_json_fields() {
        manager.save(bo());

        ArgumentCaptor<ApiKeyDO> captor = ArgumentCaptor.forClass(ApiKeyDO.class);
        verify(apiKeyMapper).insert(captor.capture());
        ApiKeyDO dataObject = captor.getValue();
        assertThat(dataObject.getKeyHash()).isEqualTo(KEY_HASH);
        assertThat(dataObject.getModels()).isEqualTo("[\"chat\",\"reasoner\"]");
        assertThat(dataObject.getMetadata()).isEqualTo("{\"user\":\"tester\"}");
        assertThat(dataObject.getSpend()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(dataObject.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void find_parses_json_fields() {
        when(apiKeyMapper.selectByKeyHash(KEY_HASH)).thenReturn(Optional.of(dataObject()));

        ApiKeyBO apiKey = manager.findByKeyHash(KEY_HASH).orElseThrow();

        assertThat(apiKey.getModels()).containsExactly("chat", "reasoner");
        assertThat(apiKey.getMetadata()).containsEntry("user", "tester");
        assertThat(apiKey.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(apiKey.isBlocked()).isFalse();
    }

    @Test
    void update_blocked_delegates_to_mapper() {
        when(apiKeyMapper.updateBlocked(KEY_HASH, true)).thenReturn(1);

        assertThat(manager.updateBlocked(KEY_HASH, true)).isTrue();
    }

    @Test
    void rejects_invalid_models_json() {
        ApiKeyDO dataObject = dataObject();
        dataObject.setModels("not-json");
        when(apiKeyMapper.selectByKeyHash(KEY_HASH)).thenReturn(Optional.of(dataObject));

        assertThatThrownBy(() -> manager.findByKeyHash(KEY_HASH))
                .isInstanceOf(IllegalStateException.class);
    }

    private ApiKeyBO bo() {
        return new ApiKeyBO(KEY_HASH, List.of("chat", "reasoner"), BigDecimal.ONE,
                BigDecimal.TEN, 60, 1000, EXPIRES_AT, false,
                Map.of("user", "tester"), CREATED_AT);
    }

    private ApiKeyDO dataObject() {
        ApiKeyDO dataObject = new ApiKeyDO();
        dataObject.setKeyHash(KEY_HASH);
        dataObject.setModels("[\"chat\",\"reasoner\"]");
        dataObject.setMetadata("{\"user\":\"tester\"}");
        dataObject.setSpend(BigDecimal.ONE);
        dataObject.setExpiresAt(EXPIRES_AT);
        dataObject.setCreatedAt(CREATED_AT);
        return dataObject;
    }
}
