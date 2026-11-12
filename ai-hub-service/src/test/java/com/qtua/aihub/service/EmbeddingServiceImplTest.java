package com.qtua.aihub.service;

import com.qtua.aihub.api.model.req.EmbeddingReqDTO;
import com.qtua.aihub.api.model.res.EmbeddingResDTO;
import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.EmbeddingModelProvider;
import com.qtua.aihub.common.rag.EmbeddingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-3.7 /v1/embeddings 服务：输入校验、模型解析、维度与响应映射。
 */
class EmbeddingServiceImplTest {

    private static final String ALIAS = "text-embedding-v3";

    private EmbeddingModelProvider provider;
    private EmbeddingModel model;
    private EmbeddingServiceImpl service;

    @BeforeEach
    void setUp() {
        provider = mock(EmbeddingModelProvider.class);
        model = mock(EmbeddingModel.class);
        when(provider.findByAlias(ALIAS)).thenReturn(Optional.of(model));
        when(model.modelId()).thenReturn("text-embedding-v3");
        when(model.dimension()).thenReturn(3);
        when(model.embedAllWithUsage(anyList(), any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            Integer dimensions = invocation.getArgument(1);
            int size = dimensions == null ? 3 : dimensions;
            return new EmbeddingResult(texts.stream().map(text -> vector(size)).toList(), 12L);
        });
        service = new EmbeddingServiceImpl(provider, new RagProperties());
    }

    @Test
    void maps_string_input_to_openai_response() {
        EmbeddingResDTO response = service.embed(request(ALIAS, "你好", null, null));

        assertThat(response.object()).isEqualTo("list");
        assertThat(response.model()).isEqualTo(ALIAS);
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).index()).isZero();
        assertThat(response.data().get(0).embedding()).hasSize(3);
        assertThat(response.usage().promptTokens()).isEqualTo(12L);
        assertThat(response.usage().totalTokens()).isEqualTo(12L);
    }

    @Test
    void maps_array_input_and_dimensions_override() {
        EmbeddingResDTO response = service.embed(
                request(ALIAS, List.of("a", "b"), 2, "float"));

        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(1).index()).isEqualTo(1);
        assertThat(response.data().get(1).embedding()).hasSize(2);
    }

    @Test
    void unknown_model_returns_chinese_400() {
        assertThatThrownBy(() -> service.embed(request("missing", "x", null, null)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException error = (ChatCompletionException) ex;
                    assertThat(error.getMessage()).contains("不存在");
                    assertThat(error.getParam()).isEqualTo("model");
                    assertThat(error.getStatus().value()).isEqualTo(400);
                });
    }

    @Test
    void blank_input_returns_400() {
        assertThatThrownBy(() -> service.embed(request(ALIAS, " ", null, null)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("input"));
    }

    @Test
    void non_string_input_returns_400() {
        assertThatThrownBy(() -> service.embed(request(ALIAS, 123, null, null)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getMessage())
                        .contains("字符串"));
    }

    @Test
    void dimensions_above_model_limit_returns_400() {
        assertThatThrownBy(() -> service.embed(request(ALIAS, "x", 4, null)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("dimensions"));
    }

    @Test
    void rejects_non_float_encoding_format() {
        assertThatThrownBy(() -> service.embed(request(ALIAS, "x", null, "base64")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("encoding_format"));
    }

    private EmbeddingReqDTO request(String model, Object input, Integer dimensions,
                                    String encodingFormat) {
        EmbeddingReqDTO request = new EmbeddingReqDTO();
        request.setModel(model);
        request.setInput(input);
        request.setDimensions(dimensions);
        request.setEncodingFormat(encodingFormat);
        return request;
    }

    private float[] vector(int size) {
        float[] vector = new float[size];
        for (int index = 0; index < size; index++) {
            vector[index] = index;
        }
        return vector;
    }
}
