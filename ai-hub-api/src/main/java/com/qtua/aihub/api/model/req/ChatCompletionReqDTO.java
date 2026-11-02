package com.qtua.aihub.api.model.req;

import com.qtua.aihub.api.model.auth.KeyAuthDTO;

import java.util.Objects;

/**
 * OpenAI ChatCompletion 请求契约（原始报文承载）。
 *
 * <p>只保存原始 JSON，避免强类型绑定后再序列化丢失未知字段；
 * 同时携带 AuthFilter 解析出的认证上下文，供 core 做模型白名单校验。</p>
 */
public class ChatCompletionReqDTO {

    private final String rawBody;
    private final KeyAuthDTO auth;

    /**
     * 构造请求 DTO。
     *
     * @param rawBody OpenAI ChatCompletion 原始请求 JSON
     * @param auth    已认证调用方上下文
     */
    public ChatCompletionReqDTO(String rawBody, KeyAuthDTO auth) {
        this.rawBody = rawBody;
        this.auth = Objects.requireNonNull(auth, "auth must not be null");
    }

    public String getRawBody() {
        return rawBody;
    }

    public KeyAuthDTO getAuth() {
        return auth;
    }
}
