package com.qtua.aihub.integration.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenEstimate;
import com.qtua.aihub.common.metering.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiProtocolAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiProtocolAdapter adapter = new OpenAiProtocolAdapter(objectMapper);

    @Test
    void rewrites_request_model_to_upstream_model() throws Exception {
        String body = "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

        String rewritten = adapter.rewriteRequestModel(body, "deepseek-chat");

        JsonNode node = objectMapper.readTree(rewritten);
        assertEquals("deepseek-chat", node.get("model").asText());
        assertEquals("hi", node.get("messages").get(0).get("content").asText());
    }

    @Test
    void rewrites_response_model_back_to_alias() throws Exception {
        String body = "{\"id\":\"x\",\"model\":\"deepseek-chat\",\"choices\":[]}";

        String rewritten = adapter.rewriteResponseModel(body, "chat");

        JsonNode node = objectMapper.readTree(rewritten);
        assertEquals("chat", node.get("model").asText());
        assertEquals("x", node.get("id").asText());
    }

    @Test
    void rejects_invalid_json_response() {
        assertThrows(ChatCompletionException.class,
                () -> adapter.rewriteResponseModel("not-json", "chat"));
    }

    @Test
    void extracts_usage_from_response() {
        String body = "{\"id\":\"x\",\"usage\":{\"prompt_tokens\":10,"
                + "\"completion_tokens\":20,\"total_tokens\":30}}";

        TokenUsage usage = adapter.extractUsage(body);

        assertEquals(new TokenUsage(10, 20, 30), usage);
    }

    @Test
    void returns_zero_when_usage_is_missing() {
        assertEquals(TokenUsage.ZERO, adapter.extractUsage("{\"id\":\"x\"}"));
    }

    @Test
    void returns_zero_for_invalid_json() {
        assertEquals(TokenUsage.ZERO, adapter.extractUsage("not-json"));
    }

    @Test
    void clamps_negative_tokens_and_fills_total() {
        String body = "{\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":-5}}";

        TokenUsage usage = adapter.extractUsage(body);

        assertEquals(new TokenUsage(10, 0, 10), usage);
    }

    @Test
    void estimates_tokens_from_messages_and_max_tokens() {
        String body = "{\"model\":\"chat\",\"max_tokens\":100,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

        TokenEstimate estimate = adapter.estimateTokens(body, 1024);

        assertEquals(TokenEstimate.of(1, 100), estimate);
    }

    @Test
    void uses_default_max_tokens_when_absent() {
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"hello world\"}]}";

        TokenEstimate estimate = adapter.estimateTokens(body, 512);

        assertEquals(TokenEstimate.of(3, 512), estimate);
    }

    @Test
    void estimates_zero_input_for_invalid_json() {
        assertEquals(TokenEstimate.of(0, 256), adapter.estimateTokens("not-json", 256));
    }
}
