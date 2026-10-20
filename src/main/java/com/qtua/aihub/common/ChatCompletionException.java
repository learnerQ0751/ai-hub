package com.qtua.aihub.common;

import org.springframework.http.HttpStatus;

/**
 * 网关业务异常。
 *
 * <p>携带 HTTP 状态码与 OpenAI 错误体字段，由 api 层统一转换为响应。</p>
 */
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

    /**
     * 未知模型错误（400）。
     *
     * @param model 请求中的模型别名
     * @return 业务异常
     */
    public static ChatCompletionException invalidModel(String model) {
        String message = "Invalid model name passed in model=" + model
                + ". Call `/v1/models` to view available models.";
        return new ChatCompletionException(HttpStatus.BAD_REQUEST, message,
                "invalid_request_error", "model", "400", null);
    }

    /**
     * 请求参数错误（400）。
     *
     * @param message 错误描述
     * @param param   出错字段
     * @return 业务异常
     */
    public static ChatCompletionException invalidRequest(String message, String param) {
        return new ChatCompletionException(HttpStatus.BAD_REQUEST, message,
                "invalid_request_error", param, "400", null);
    }

    /**
     * 上游鉴权失败（401）。
     *
     * @param cause 原始异常
     * @return 业务异常
     */
    public static ChatCompletionException upstreamAuthError(Throwable cause) {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED, "Upstream authentication failed",
                "auth_error", null, "401", cause);
    }

    /**
     * 上游超时（504）。
     *
     * @param cause 原始异常
     * @return 业务异常
     */
    public static ChatCompletionException upstreamTimeout(Throwable cause) {
        return new ChatCompletionException(HttpStatus.GATEWAY_TIMEOUT, "Upstream request timed out",
                "timeout_error", null, "504", cause);
    }

    /**
     * 上游其他失败（502）。
     *
     * @param message 错误描述
     * @param cause   原始异常
     * @return 业务异常
     */
    public static ChatCompletionException upstreamError(String message, Throwable cause) {
        return new ChatCompletionException(HttpStatus.BAD_GATEWAY, message,
                "upstream_error", null, "502", cause);
    }

    /**
     * 网关内部错误（500）。
     *
     * @param message 错误描述
     * @param cause   原始异常
     * @return 业务异常
     */
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
