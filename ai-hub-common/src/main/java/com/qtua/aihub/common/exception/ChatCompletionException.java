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
        String message = "模型 '" + model + "' 不存在或不可用，请调用 /v1/models 查看可用模型。";
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
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED, "上游服务鉴权失败",
                "auth_error", null, "401", cause, true);
    }

    /**
     * 上游超时（504）。
     *
     * @param cause 原始异常
     * @return 业务异常
     */
    public static ChatCompletionException upstreamTimeout(Throwable cause) {
        return new ChatCompletionException(HttpStatus.GATEWAY_TIMEOUT, "上游请求超时",
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
                "上游服务触发限流", "upstream_error", null, "502", cause, true);
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
                "上游拒绝了本次请求，状态码 " + upstreamStatus,
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
        String message = "当前 API Key 无权访问模型 '" + model
                + "'，或模型名不存在；请检查可用模型后重试。";
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
                "鉴权失败：API Key 无效", "auth_error", null, "401", null, false);
    }

    /**
     * 虚拟 Key 已封禁（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException blockedApiKey() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "鉴权失败：该 API Key 已被封禁，请联系管理员解封。",
                "auth_error", null, "401", null, false);
    }

    /**
     * 虚拟 Key 已过期（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException expiredApiKey() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "鉴权失败：该 API Key 已过期。", "auth_error", null, "401", null, false);
    }

    /**
     * 管理接口要求 master key（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException masterKeyRequired() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "鉴权失败：管理接口需要 master key。", "auth_error", null, "401", null, false);
    }

    /**
     * Key 级限流命中（429）。
     *
     * @param detail 触发的限制描述，如 rpm/tpm
     * @return 业务异常
     */
    public static ChatCompletionException rateLimitExceeded(String detail) {
        return new ChatCompletionException(HttpStatus.TOO_MANY_REQUESTS,
                "触发限流：" + detail, "rate_limit_error", null, "429", null, false);
    }

    /**
     * Key 预算耗尽（400）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException budgetExceeded() {
        return new ChatCompletionException(HttpStatus.BAD_REQUEST,
                "该 API Key 的预算已耗尽。", "budget_exceeded", null, "400", null, false);
    }

    /**
     * 查询/封禁的 Key 不存在（404）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException apiKeyNotFound() {
        return new ChatCompletionException(HttpStatus.NOT_FOUND,
                "API Key 不存在", "not_found_error", "key", "404", null, false);
    }

    /**
     * MCP Server 不存在（404）。
     *
     * @param serverId 服务器 ID
     * @return 业务异常
     */
    public static ChatCompletionException mcpServerNotFound(String serverId) {
        return new ChatCompletionException(HttpStatus.NOT_FOUND,
                "MCP Server '" + serverId + "' 不存在", "not_found_error",
                "server_id", "404", null, false);
    }

    /**
     * MCP 工具不存在（404）。
     *
     * @param toolName 对外工具名
     * @return 业务异常
     */
    public static ChatCompletionException mcpToolNotFound(String toolName) {
        return new ChatCompletionException(HttpStatus.NOT_FOUND,
                "MCP 工具 '" + toolName + "' 不存在，请刷新工具目录后重试",
                "not_found_error", "tool", "404", null, false);
    }

    /**
     * Prompt 模板不存在或无已发布版本（404）。
     *
     * @param name 模板名
     * @return 业务异常
     */
    public static ChatCompletionException promptNotFound(String name) {
        return new ChatCompletionException(HttpStatus.NOT_FOUND,
                "Prompt '" + name + "' 不存在或无已发布版本", "not_found_error",
                "name", "404", null, false);
    }

    /**
     * Prompt 指定版本不存在（404）。
     *
     * @param name    模板名
     * @param version 版本
     * @return 业务异常
     */
    public static ChatCompletionException promptVersionNotFound(String name, int version) {
        return new ChatCompletionException(HttpStatus.NOT_FOUND,
                "Prompt '" + name + "' 的版本 " + version + " 不存在", "not_found_error",
                "version", "404", null, false);
    }

    /**
     * MCP Server 名称冲突（409）。
     *
     * @param name 冲突的名称
     * @return 业务异常
     */
    public static ChatCompletionException mcpServerConflict(String name) {
        return new ChatCompletionException(HttpStatus.CONFLICT,
                "MCP Server 名称 '" + name + "' 已存在", "conflict_error",
                "name", "409", null, false);
    }

    /**
     * MCP Server 注册参数非法（400）。
     *
     * @param message 错误描述
     * @param param   出错字段
     * @return 业务异常
     */
    public static ChatCompletionException invalidMcpServerRequest(String message, String param) {
        return new ChatCompletionException(HttpStatus.BAD_REQUEST, message,
                "invalid_request_error", param, "400", null, false);
    }

    /**
     * 记忆访问被拒绝（403）。
     *
     * <p>典型场景：未开启跨项目共享却请求跨项目召回。错误体字段保持英文协议值。</p>
     *
     * @param message 中文错误描述
     * @return 业务异常
     */
    public static ChatCompletionException memoryAccessDenied(String message) {
        return new ChatCompletionException(HttpStatus.FORBIDDEN, message,
                "memory_access_denied", "cross_project", "403", null, false);
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
