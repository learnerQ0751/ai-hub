package com.qtua.aihub.integration.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.exception.ChatCompletionException;
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
}
