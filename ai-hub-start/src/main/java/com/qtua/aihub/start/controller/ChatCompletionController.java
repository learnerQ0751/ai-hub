package com.qtua.aihub.start.controller;

import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAI 兼容 Chat Completions HTTP 入口。
 *
 * <p>只负责 HTTP 契约与响应包装，不包含业务编排；业务逻辑在 service/core。</p>
 */
@RestController
public class ChatCompletionController {

    private final ChatCompletionService chatCompletionService;

    /**
     * 构造控制器。
     *
     * @param chatCompletionService 聊天补全应用服务
     */
    public ChatCompletionController(ChatCompletionService chatCompletionService) {
        this.chatCompletionService = chatCompletionService;
    }

    /**
     * 接收 OpenAI 兼容请求并返回上游响应。
     *
     * <p>同时支持 /v1/chat/completions 与 /chat/completions，兼容不同 SDK 的默认路径。</p>
     *
     * @param requestBody 原始 JSON 请求体
     * @return 上游 ChatCompletion JSON（原样透传，仅约定字段改写）
     */
    @PostMapping(path = {"/v1/chat/completions", "/chat/completions"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@RequestBody String requestBody) {
        ChatCompletionResDTO resDTO = chatCompletionService.chat(new ChatCompletionReqDTO(requestBody));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resDTO.getRawResponse());
    }
}
