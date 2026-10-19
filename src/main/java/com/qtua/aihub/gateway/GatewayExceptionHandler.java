package com.qtua.aihub.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(ChatCompletionException.class)
    public ResponseEntity<ErrorResponse> handleChatCompletion(ChatCompletionException ex) {
        LOG.warn("gateway request failed: type={}, code={}, message={}",
                ex.getType(), ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ex.getMessage(), ex.getType(), ex.getParam(), ex.getCode()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        LOG.warn("request body is not readable: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("Invalid JSON request body", "invalid_request_error", null, "400"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        LOG.error("unexpected gateway error", ex);
        return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("Internal gateway error", "internal_error", null, "500"));
    }
}
