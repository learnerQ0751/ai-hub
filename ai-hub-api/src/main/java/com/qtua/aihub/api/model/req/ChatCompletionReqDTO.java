package com.qtua.aihub.api.model.req;

/**
 * OpenAI ChatCompletion 请求契约（原始报文承载）。
 *
 * <p>只保存原始 JSON，避免强类型绑定后再序列化丢失未知字段。</p>
 */
public class ChatCompletionReqDTO {

    private final String rawBody;

    /**
     * 构造请求 DTO。
     *
     * @param rawBody OpenAI ChatCompletion 原始请求 JSON
     */
    public ChatCompletionReqDTO(String rawBody) {
        this.rawBody = rawBody;
    }

    public String getRawBody() {
        return rawBody;
    }
}
