package com.qtua.aihub.gateway;

import org.springframework.http.HttpStatus;

public class ChatCompletionException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String param;
    private final String code;

    private ChatCompletionException(HttpStatus status, String message, String type,
                                    String param, String code, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.type = type;
        this.param = param;
        this.code = code;
    }

    public static ChatCompletionException invalidModel(String model) {
        String message = "Invalid model name passed in model=" + model
                + ". Call `/v1/models` to view available models.";
        return new ChatCompletionException(HttpStatus.BAD_REQUEST, message,
                "invalid_request_error", "model", "400", null);
    }

    public static ChatCompletionException invalidRequest(String message, String param) {
        return new ChatCompletionException(HttpStatus.BAD_REQUEST, message,
                "invalid_request_error", param, "400", null);
    }

    public static ChatCompletionException upstreamAuthError(Throwable cause) {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED, "Upstream authentication failed",
                "auth_error", null, "401", cause);
    }

    public static ChatCompletionException upstreamTimeout(Throwable cause) {
        return new ChatCompletionException(HttpStatus.GATEWAY_TIMEOUT, "Upstream request timed out",
                "timeout_error", null, "504", cause);
    }

    public static ChatCompletionException upstreamError(String message, Throwable cause) {
        return new ChatCompletionException(HttpStatus.BAD_GATEWAY, message,
                "upstream_error", null, "502", cause);
    }

    public static ChatCompletionException internalError(String message, Throwable cause) {
        return new ChatCompletionException(HttpStatus.INTERNAL_SERVER_ERROR, message,
                "internal_error", null, "500", cause);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getParam() {
        return param;
    }

    public String getCode() {
        return code;
    }
}
