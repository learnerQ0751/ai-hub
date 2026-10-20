package com.qtua.aihub.manager.model;

/**
 * 聊天补全结果业务对象。
 */
public class ChatCompletionResultBO {

    private final String responseBody;

    /**
     * 构造结果对象。
     *
     * @param responseBody 上游原始响应 JSON
     */
    public ChatCompletionResultBO(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
