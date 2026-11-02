package com.qtua.aihub.service;

import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.KeyGenerateReqDTO;
import com.qtua.aihub.api.model.res.KeyBlockResDTO;
import com.qtua.aihub.api.model.res.KeyGenerateResDTO;
import com.qtua.aihub.api.model.res.KeyInfoResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.util.KeyHasher;
import com.qtua.aihub.manager.ApiKeyManager;
import com.qtua.aihub.manager.model.ApiKeyBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeyServiceTest {

    private static final String PLAIN_KEY = "sk-abcdefghijklmnop";
    private static final String HASH = KeyHasher.sha256Hex(PLAIN_KEY);

    private ApiKeyManager manager;
    private KeyServiceImpl service;

    @BeforeEach
    void setUp() {
        manager = mock(ApiKeyManager.class);
        service = new KeyServiceImpl(manager);
    }

    @Test
    void generate_returns_plain_key_and_persists_only_hash() {
        KeyGenerateReqDTO request = new KeyGenerateReqDTO(
                List.of("chat"), "1h", Map.of("user", "tester"),
                BigDecimal.TEN, 60, 1000);

        KeyGenerateResDTO response = service.generate(request);

        assertThat(response.key()).startsWith("sk-").hasSize(51);
        assertThat(response.models()).containsExactly("chat");
        assertThat(response.metadata()).containsEntry("user", "tester");
        assertThat(response.spend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.blocked()).isFalse();
        assertThat(response.expires()).isNotBlank();

        ArgumentCaptor<ApiKeyBO> captor = ArgumentCaptor.forClass(ApiKeyBO.class);
        verify(manager).save(captor.capture());
        ApiKeyBO saved = captor.getValue();
        assertThat(saved.getKeyHash()).isEqualTo(KeyHasher.sha256Hex(response.key()));
        assertThat(saved.getKeyHash()).isNotEqualTo(response.key());
        assertThat(saved.getModels()).containsExactly("chat");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(59, ChronoUnit.MINUTES));
    }

    @Test
    void generate_without_duration_has_no_expiry() {
        KeyGenerateResDTO response = service.generate(
                new KeyGenerateReqDTO(List.of(), null, null, null, null, null));

        assertThat(response.expires()).isNull();
        ArgumentCaptor<ApiKeyBO> captor = ArgumentCaptor.forClass(ApiKeyBO.class);
        verify(manager).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isNull();
        assertThat(captor.getValue().getModels()).isEmpty();
    }

    @Test
    void generate_rejects_invalid_duration_format() {
        assertThatThrownBy(() -> service.generate(
                new KeyGenerateReqDTO(List.of(), "abc", null, null, null, null)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam()).isEqualTo("duration"));
        verify(manager, never()).save(any(ApiKeyBO.class));
    }

    @Test
    void generate_rejects_non_positive_duration() {
        assertThatThrownBy(() -> service.generate(
                new KeyGenerateReqDTO(List.of(), "0h", null, null, null, null)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam()).isEqualTo("duration"));
    }

    @Test
    void authenticate_returns_models_for_valid_key() {
        when(manager.findByKeyHash(HASH)).thenReturn(Optional.of(apiKey(false, null)));

        KeyAuthDTO auth = service.authenticate(PLAIN_KEY);

        assertThat(auth.masterKey()).isFalse();
        assertThat(auth.keyHash()).isEqualTo(HASH);
        assertThat(auth.models()).containsExactly("chat");
    }

    @Test
    void authenticate_rejects_unknown_key() {
        when(manager.findByKeyHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("sk-unknown"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType()).isEqualTo("auth_error"));
    }

    @Test
    void authenticate_rejects_blank_key() {
        assertThatThrownBy(() -> service.authenticate(" "))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType()).isEqualTo("auth_error"));
        verify(manager, never()).findByKeyHash(anyString());
    }

    @Test
    void authenticate_rejects_blocked_key() {
        when(manager.findByKeyHash(HASH)).thenReturn(Optional.of(apiKey(true, null)));

        assertThatThrownBy(() -> service.authenticate(PLAIN_KEY))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("blocked"));
    }

    @Test
    void authenticate_rejects_expired_key() {
        when(manager.findByKeyHash(HASH))
                .thenReturn(Optional.of(apiKey(false, Instant.now().minus(1, ChronoUnit.MINUTES))));

        assertThatThrownBy(() -> service.authenticate(PLAIN_KEY))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("expired"));
    }

    @Test
    void info_returns_masked_key_and_spend() {
        when(manager.findByKeyHash(HASH)).thenReturn(Optional.of(apiKey(false, null)));

        KeyInfoResDTO response = service.info(PLAIN_KEY);

        assertThat(response.key()).isEqualTo("sk-****mnop");
        assertThat(response.info().spend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.info().models()).containsExactly("chat");
        assertThat(response.info().metadata()).containsEntry("user", "tester");
        assertThat(response.info().lastActive()).isNull();
    }

    @Test
    void info_rejects_unknown_key() {
        when(manager.findByKeyHash(HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.info(PLAIN_KEY))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getCode()).isEqualTo("404"));
    }

    @Test
    void info_requires_key_parameter() {
        assertThatThrownBy(() -> service.info(null))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam()).isEqualTo("key"));
    }

    @Test
    void block_and_unblock_update_state() {
        when(manager.findByKeyHash(HASH)).thenReturn(Optional.of(apiKey(false, null)));
        when(manager.updateBlocked(HASH, true)).thenReturn(true);
        when(manager.updateBlocked(HASH, false)).thenReturn(true);

        KeyBlockResDTO blocked = service.block(PLAIN_KEY);
        KeyBlockResDTO unblocked = service.unblock(PLAIN_KEY);

        assertThat(blocked.blocked()).isTrue();
        assertThat(unblocked.blocked()).isFalse();
        verify(manager).updateBlocked(HASH, true);
        verify(manager).updateBlocked(HASH, false);
    }

    private ApiKeyBO apiKey(boolean blocked, Instant expiresAt) {
        return new ApiKeyBO(HASH, List.of("chat"), BigDecimal.ZERO, BigDecimal.TEN,
                60, 1000, expiresAt, blocked, Map.of("user", "tester"), Instant.now());
    }
}
