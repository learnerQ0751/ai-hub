package com.qtua.aihub.integration.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.rag.EmbeddingResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-3.7 远端 embedding：批量拆分、重试、维度校验、usage 计量与中文错误映射。
 */
class RemoteEmbeddingModelTest {

    private static final int DIMENSION = 3;
    private static final int MAX_INPUT_TOKENS = 20;
    private static final int MAX_BATCH = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;
    private RemoteEmbeddingModel model;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        model = model("upstream-key", MAX_BATCH, 1, 0L);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void splits_batches_and_accumulates_provider_usage() throws Exception {
        for (int batch = 0; batch < 3; batch++) {
            server.enqueue(response(200, batchBody(7, DIMENSION,
                    batch < 2 ? List.of(0, 1) : List.of(0))));
        }

        EmbeddingResult result = model.embedAllWithUsage(List.of("a", "b", "c", "d", "e"), null);

        assertThat(result.vectors()).hasSize(5);
        assertThat(result.inputTokens()).isEqualTo(21);
        for (int batch = 0; batch < 3; batch++) {
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/embeddings");
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer upstream-key");
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
            assertThat(body.path("model").asText()).isEqualTo("text-embedding-v3");
            assertThat(body.path("encoding_format").asText()).isEqualTo("float");
            int expectedSize = batch < 2 ? 2 : 1;
            assertThat(body.path("input").size()).isEqualTo(expectedSize);
            assertThat(body.has("dimensions")).isFalse();
        }
    }

    @Test
    void retries_retryable_failure_then_succeeds() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(response(200, batchBody(5, DIMENSION, List.of(0))));

        EmbeddingResult result = model.embedAllWithUsage(List.of("hello"), null);

        assertThat(result.vectors()).hasSize(1);
        assertThat(result.inputTokens()).isEqualTo(5);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void missing_api_key_returns_chinese_configuration_error() {
        RemoteEmbeddingModel misconfigured = model(null, MAX_BATCH, 0, 0L);

        assertThatThrownBy(() -> misconfigured.embedAllWithUsage(List.of("hello"), null))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException error = (ChatCompletionException) ex;
                    assertThat(error.getMessage()).contains("API Key");
                    assertThat(error.getType()).isEqualTo("upstream_error");
                    assertThat(error.getStatus().value()).isEqualTo(502);
                });
    }

    @Test
    void rejects_text_over_model_window() {
        assertThatThrownBy(() -> model.embedAllWithUsage(
                List.of("这是一段明显超过模型窗口的文本内容用于测试"), null))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException error = (ChatCompletionException) ex;
                    assertThat(error.getMessage()).contains("最大输入");
                    assertThat(error.getParam()).isEqualTo("input");
                });
    }

    @Test
    void rejects_response_dimension_mismatch() {
        server.enqueue(response(200, batchBody(3, 2, List.of(0))));

        assertThatThrownBy(() -> model.embedAllWithUsage(List.of("hello"), null))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getMessage())
                        .contains("维度"));
    }

    @Test
    void passes_requested_dimensions_and_validates_result() throws Exception {
        server.enqueue(response(200, batchBody(4, 2, List.of(0))));

        EmbeddingResult result = model.embedAllWithUsage(List.of("hello"), 2);

        assertThat(result.vectors().get(0)).hasSize(2);
        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertThat(body.path("dimensions").asInt()).isEqualTo(2);
    }

    @Test
    void sorts_vectors_by_index_when_provider_returns_out_of_order() {
        String body = "{\"data\":[{\"index\":1,\"embedding\":[1,1,1]},"
                + "{\"index\":0,\"embedding\":[0,0,0]}],"
                + "\"usage\":{\"prompt_tokens\":4}}";
        server.enqueue(response(200, body));

        EmbeddingResult result = model.embedAllWithUsage(List.of("a", "b"), null);

        assertThat(result.vectors().get(0)).containsExactly(0.0f, 0.0f, 0.0f);
        assertThat(result.vectors().get(1)).containsExactly(1.0f, 1.0f, 1.0f);
    }

    private RemoteEmbeddingModel model(String apiKey, int maxBatch, int maxRetries,
                                       long backoff) {
        EmbeddingModelDefinition definition = new EmbeddingModelDefinition(
                "text-embedding-v3", "dashscope", "text-embedding-v3", DIMENSION,
                MAX_INPUT_TOKENS, maxBatch, new BigDecimal("0.5"),
                server.url("/").toString(), apiKey, "AI_HUB_TEST_MISSING_KEY",
                2_000, maxRetries, backoff, 1);
        return new RemoteEmbeddingModel(definition, objectMapper);
    }

    private String batchBody(long promptTokens, int dimension, List<Integer> indexes) {
        StringBuilder data = new StringBuilder();
        for (int position = 0; position < indexes.size(); position++) {
            if (position > 0) {
                data.append(',');
            }
            data.append("{\"index\":").append(indexes.get(position)).append(",\"embedding\":[");
            for (int index = 0; index < dimension; index++) {
                if (index > 0) {
                    data.append(',');
                }
                data.append(indexes.get(position)).append(".0");
            }
            data.append("]}");
        }
        return "{\"data\":[" + data + "],\"usage\":{\"prompt_tokens\":" + promptTokens
                + ",\"total_tokens\":" + promptTokens + "}}";
    }

    private MockResponse response(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json").setBody(body);
    }
}
