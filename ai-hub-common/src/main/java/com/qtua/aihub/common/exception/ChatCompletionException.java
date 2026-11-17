package com.qtua.aihub.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

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
     * 上游响应非法（502，不可重试）。
     *
     * <p>响应结构/维度/JSON 错误重试无意义，直接返回中文错误，避免无效重试放大延迟。</p>
     *
     * @param message 错误描述
     * @return 业务异常
     */
    public static ChatCompletionException upstreamInvalidResponse(String message) {
        return new ChatCompletionException(HttpStatus.BAD_GATEWAY, message,
                "upstream_error", null, "502", null, false);
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
     * 控制台登录失败（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException consoleLoginFailed() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "用户名或密码错误。", "auth_error", null, "401", null, false);
    }

    /**
     * 控制台会话未启用或未配置账号（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException consoleSessionDisabled() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "控制台会话登录未启用，请使用 master key。", "auth_error", null, "401", null, false);
    }

    /**
     * 控制台登录连续失败被锁定（429）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException consoleLoginLocked() {
        return new ChatCompletionException(HttpStatus.TOO_MANY_REQUESTS,
                "登录失败次数过多，请稍后重试。", "rate_limit_error", null, "429", null, false);
    }

    /**
     * 控制台会话无效或已过期（401）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException consoleSessionRequired() {
        return new ChatCompletionException(HttpStatus.UNAUTHORIZED,
                "登录状态已失效，请重新登录。", "auth_error", null, "401", null, false);
    }

    /**
     * 管理面权限不足（403，REQ-4.4）。
     *
     * @return 业务异常
     */
    public static ChatCompletionException adminRequired() {
        return new ChatCompletionException(HttpStatus.FORBIDDEN,
                "当前调用方没有管理权限。", "authorization_error", null, "403", null, false);
    }

    /**
     * 管理凭证被用于数据面（403，REQ-5.1）。
     *
     * <p>拆分后 master key 只属于控制面；数据面只接受虚拟 Key，避免管理凭证
     * 直接出现在请求路径上。</p>
     *
     * @return 业务异常
     */
    public static ChatCompletionException adminKeyNotAllowed() {
        return new ChatCompletionException(HttpStatus.FORBIDDEN,
                "数据面不接受管理凭证，请使用虚拟 API Key。",
                "authorization_error", null, "403", null, false);
    }

    /**
     * 租户/项目越权（403，REQ-4.1）。
     *
     * <p>错误体字段保持英文协议值；日志由 {@code TenantScopeGuard} 记录 Key 哈希与范围。</p>
     *
     * @param reason           中文原因
     * @param requestedTenant  请求声明的租户
     * @param requestedProject 请求声明的项目
     * @return 业务异常
     */
    public static ChatCompletionException tenantAccessDenied(String reason, String requestedTenant,
                                                             String requestedProject) {
        String param = StringUtils.hasText(requestedTenant) ? "tenant_id" : "project_id";
        return new ChatCompletionException(HttpStatus.FORBIDDEN, reason,
                "tenant_access_denied", param, "403", null, false);
    }

    /**
     * 模型别名不存在（404）。
     *
     * @param alias 别名
     * @return 业务异常
     */
    public static ChatCompletionException modelAliasNotFound(String alias) {
        return new ChatCompletionException(HttpStatus.NOT_FOUND,
                "模型别名 '" + alias + "' 不存在", "not_found_error",
                "alias", "404", null, false);
    }

    /**
     * Trace 不存在或不属于当前租户（404）。
     *
     * @param traceId 追踪 ID
     * @return 业务异常
     */
    public static ChatCompletionException traceNotFound(String traceId) {
        return new ChatCompletionException(HttpStatus.NOT_FOUND,
                "Trace '" + traceId + "' 不存在", "not_found_error",
                "trace_id", "404", null, false);
    }

    /**
     * 资源不属于当前租户（404，避免泄露存在性）。
     *
     * @param message 中文描述
     * @return 业务异常
     */
    public static ChatCompletionException tenantResourceNotFound(String message) {
        return new ChatCompletionException(HttpStatus.NOT_FOUND, message,
                "not_found_error", null, "404", null, false);
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
     * embedding 模型配置不完整（502）。
     *
     * <p>典型场景：远端模型未设置 API Key / base-url。错误信息中文，协议字段保持英文。</p>
     *
     * @param detail 缺少的配置描述
     * @return 业务异常
     */
    public static ChatCompletionException embeddingNotConfigured(String detail) {
        return new ChatCompletionException(HttpStatus.BAD_GATEWAY,
                "embedding 模型未正确配置：" + detail, "upstream_error", null, "502", null, false);
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

    /**
     * RAG 文档不存在或不属于当前项目（404）。
     *
     * <p>跨项目访问统一返回 404，避免泄露文档是否存在。</p>
     *
     * @param docId 文档 ID
     * @return 业务异常
     */
    public static ChatCompletionException ragDocumentNotFound(String docId) {
        return new ChatCompletionException(HttpStatus.NOT_FOUND,
                "RAG 文档 '" + docId + "' 不存在", "not_found_error",
                "doc_id", "404", null, false);
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
