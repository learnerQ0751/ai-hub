package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void serializes_openai_error_shape() throws Exception {
        ErrorResponse response = ErrorResponse.of("bad model", "invalid_request_error", "model", "400");

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json)
                .contains("\"error\"")
                .contains("\"message\":\"bad model\"")
                .contains("\"type\":\"invalid_request_error\"")
                .contains("\"param\":\"model\"")
                .contains("\"code\":\"400\"");
    }
}
