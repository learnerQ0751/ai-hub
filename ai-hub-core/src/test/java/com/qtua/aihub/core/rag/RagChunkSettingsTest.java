package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.rag.EmbeddingModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-3.8 chunk 参数推导：按模型窗口推导、显式配置优先、越界 warn/fail。
 */
class RagChunkSettingsTest {

    @Test
    void derives_800_chars_for_v3_window() {
        RagProperties properties = new RagProperties();
        RagChunkSettings settings = new RagChunkSettings(properties, model(8192));

        assertThat(settings.maxChars()).isEqualTo(800);
        assertThat(settings.overlapChars()).isEqualTo(100);
    }

    @Test
    void derives_small_window_for_local_256_tokens() {
        RagProperties properties = new RagProperties();
        RagChunkSettings settings = new RagChunkSettings(properties, model(256));

        assertThat(settings.maxChars()).isEqualTo(204);
    }

    @Test
    void explicit_config_wins() {
        RagProperties properties = new RagProperties();
        properties.getChunk().setMaxChars(120);
        properties.getChunk().setOverlapChars(20);
        RagChunkSettings settings = new RagChunkSettings(properties, model(8192));

        assertThat(settings.maxChars()).isEqualTo(120);
        assertThat(settings.overlapChars()).isEqualTo(20);
    }

    @Test
    void overflow_warns_by_default() {
        RagProperties properties = new RagProperties();
        properties.getChunk().setMaxChars(5000);
        RagChunkSettings settings = new RagChunkSettings(properties, model(256));

        assertThat(settings.maxChars()).isEqualTo(5000);
    }

    @Test
    void overflow_fails_when_configured() {
        RagProperties properties = new RagProperties();
        properties.getChunk().setMaxChars(5000);
        properties.getChunk().setOverflowPolicy("fail");

        assertThatThrownBy(() -> new RagChunkSettings(properties, model(256)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("safety window");
    }

    private EmbeddingModel model(int maxInputTokens) {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.maxInputTokens()).thenReturn(maxInputTokens);
        return model;
    }
}
