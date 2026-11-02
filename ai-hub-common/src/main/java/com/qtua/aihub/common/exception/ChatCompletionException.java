package com.qtua.aihub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 网关业务异常。
 *
 * <p>携带 HTTP 状态码与 OpenAI 错误体字段，由 api 层统一转换为响应。
 * {@code retryable} 表示该错误是否允许进入容错流程（同部署重试 / 主备切换 / fallback）：
 * 只有部署或上游侧的问题才可重试；请求参数类错误重试无意义。</p>
 */
public class ChatCompletionException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String param;
    private final String code;
    private final boolean retryable;

    private ChatCompletionException(HttpStatus status, String message, String type,
                                    String param, String code, Throwable cause,
                                    boolean retryable) {
        super(message, cause);
        this.status = status;
        this.type = type;
        this.param = param;
        this.code = code;
        this.retryable = retryable;
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
                "invalid_request_error", "model", "400", null, false);
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
                "invalid_request_error", param, "400", null, false);
    }

    /**
     * 上游鉴权失败（401）。
     *
     * <p>通常是该部署的 api-key 配置错误；允许切换到其他部署或 fallback 别名（B-11 场景）。</p>
     *
     * @param cause 原始异常
     * @return 业务异常
     */
    public static ChatCompletionException upstreamAuthError(Throwable cause) {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED, "Upstream authentication failed",
                "auth_error", null, "401", cause, true);
    }

    /**
     * 上游超时（504）。
     *
     * @param cause 原始异常
     * @return 业务异常
     */
    public static ChatCompletionException upstreamTimeout(Throwable cause) {
        return new ChatCompletionException(HttpStatus.GATEWAY_TIMEOUT, "Upstream request timed out",
                "timeout_error", null, "504", cause, true);
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
                "upstream_error", null, "502", cause, true);
    }

    /**
     * 上游限流（对外 502，内部标记可重试）。
     *
     * <p>上游 429 与本地 Key 限流语义不同：上游限流可通过切换部署/别名规避，
     * 对外仍按上游错误返回，避免与本地 rate_limit_error 混淆。</p>
     *
     * @param cause 原始异常
     * @return 业务异常
     */
    public static ChatCompletionException upstreamRateLimited(Throwable cause) {
        return new ChatCompletionException(HttpStatus.BAD_GATEWAY,
                "Upstream rate limit exceeded", "upstream_error", null, "502", cause, true);
    }

    /**
     * 上游客户端错误（4xx），不可重试。
     *
     * <p>上游 400/404/422 多为请求自身问题，换部署仍会失败；对外保持原上游错误语义。</p>
     *
     * @param upstreamStatus 上游状态码
     * @param cause          原始异常
     * @return 业务异常
     */
    public static ChatCompletionException upstreamClientError(int upstreamStatus, Throwable cause) {
        return new ChatCompletionException(HttpStatus.BAD_GATEWAY,
                "Upstream rejected the request with status " + upstreamStatus,
                "upstream_error", null, "502", cause, false);
    }

    /**
     * 虚拟 Key 无权访问该模型（403）。
     *
     * <p>错误体对齐 LiteLLM：type=key_model_access_denied、param=model、code=403。</p>
     *
     * @param model 请求中的模型别名
     * @return 业务异常
     */
    public static ChatCompletionException keyModelAccessDenied(String model) {
        String message = "The requested model '" + model + "' is not available for this API key, "
                + "or the model name is invalid. Check the models available to you and try again.";
        return new ChatCompletionException(HttpStatus.FORBIDDEN, message,
                "key_model_access_denied", "model", "403", null, false);
    }

    /**
     * 虚拟 Key 不存在或已失效（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException invalidApiKey() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "Authentication Error, invalid API key", "auth_error", null, "401", null, false);
    }

    /**
     * 虚拟 Key 已封禁（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException blockedApiKey() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "Authentication Error, Key is blocked. Update via `/key/unblock` if you're an admin.",
                "auth_error", null, "401", null, false);
    }

    /**
     * 虚拟 Key 已过期（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException expiredApiKey() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "Authentication Error, Key is expired.", "auth_error", null, "401", null, false);
    }

    /**
     * 管理接口要求 master key（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException masterKeyRequired() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "Authentication Error, master key required", "auth_error", null, "401", null, false);
    }

    /**
     * Key 级限流命中（429）。
     *
     * @param detail 触发的限制描述，如 rpm/tpm
     * @return 业务异常
     */
    public static ChatCompletionException rateLimitExceeded(String detail) {
        return new ChatCompletionException(HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded: " + detail, "rate_limit_error", null, "429", null, false);
    }

    /**
     * Key 预算耗尽（400）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException budgetExceeded() {
        return new ChatCompletionException(HttpStatus.BAD_REQUEST,
                "Budget exceeded for this API key", "budget_exceeded", null, "400", null, false);
    }

    /**
     * 查询/封禁的 Key 不存在（404）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException apiKeyNotFound() {
        return new ChatCompletionException(HttpStatus.NOT_FOUND,
                "API key not found", "not_found_error", "key", "404", null, false);
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
                "internal_error", null, "500", cause, false);
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

    /**
     * 是否允许进入容错流程。
     *
     * @return true 表示可重试或可切换部署
     */
    public boolean isRetryable() {
        return retryable;
    }
}
