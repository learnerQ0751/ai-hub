package com.qtua.aihub.api.model.res;

/**
 * OpenAI ChatCompletion 响应契约（原始报文承载）。
 */
public class ChatCompletionResDTO {

    private final String rawResponse;

    /**
     * 构造响应 DTO。
     *
     * @param rawResponse 上游原始响应 JSON
     */
    public ChatCompletionResDTO(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
