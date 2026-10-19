package com.qtua.aihub.gateway;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatCompletionController {

    private final ChatCompletionService chatCompletionService;

    public ChatCompletionController(ChatCompletionService chatCompletionService) {
        this.chatCompletionService = chatCompletionService;
    }

    @PostMapping(path = {"/v1/chat/completions", "/chat/completions"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@RequestBody String requestBody) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(chatCompletionService.chat(requestBody));
    }
}
