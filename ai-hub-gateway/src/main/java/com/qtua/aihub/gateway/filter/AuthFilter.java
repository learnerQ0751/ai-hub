package com.qtua.aihub.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.res.ErrorResponse;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.tenant.ProjectAuthorizationPort;
import com.qtua.aihub.common.tenant.TenantContextHolder;
import com.qtua.aihub.common.tenant.TenantIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 数据面鉴权过滤器（REQ-5.1）。
 *
 * <p>只接受虚拟 Key：通过 {@link KeyService#authenticate(String)} 查库校验存在性、
 * 封禁与过期。master key 属于控制面，出现在数据面请求上直接 403。认证结果写入
 * 请求属性 {@link #KEY_AUTH_ATTRIBUTE}，供 Controller/Service 使用。</p>
 *
 * <p>安全约束：不记录明文 Key；master key 比较使用常量时间比较，降低时序侧信道风险。
 * 未配置 master key 时进入本地放行模式（仅限本地开发）。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthFilter extends OncePerRequestFilter {

    /** 请求属性名：认证上下文（{@link KeyAuthDTO}）。 */
    public static final String KEY_AUTH_ATTRIBUTE = "gateway.key.auth";

    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayProperties properties;
    private final KeyService keyService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ProjectAuthorizationPort> authorizationPortProvider;

    /**
     * 构造过滤器。
     *
     * @param properties                网关配置
     * @param keyService                虚拟 Key 鉴权服务
     * @param objectMapper              JSON 序列化器（用于写错误体）
     * @param authorizationPortProvider 项目授权端口（切片测试中可为空）
     */
    public AuthFilter(GatewayProperties properties, KeyService keyService,
                      ObjectMapper objectMapper,
                      ObjectProvider<ProjectAuthorizationPort> authorizationPortProvider) {
        this.properties = properties;
        this.keyService = keyService;
        this.objectMapper = objectMapper;
        this.authorizationPortProvider = authorizationPortProvider;
    }

    /**
     * 健康检查与信息端点不做鉴权（容器探针使用）。
     *
     * @param request HTTP 请求
     * @return true 表示跳过鉴权
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    /**
     * 完成鉴权并写入认证上下文；失败时直接返回 OpenAI 兼容错误体。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            KeyAuthDTO auth = enrich(authenticate(request));
            request.setAttribute(KEY_AUTH_ATTRIBUTE, auth);
            TenantContextHolder.set(auth.masterKey()
                    ? TenantIdentity.masterIdentity()
                    : TenantIdentity.virtualKey(auth.keyHash(), auth.tenantId(),
                            auth.projectId(), auth.authorizedProjects()));
            filterChain.doFilter(request, response);
        } catch (ChatCompletionException ex) {
            LOG.warn("authentication rejected: type={}, message={}", ex.getType(), ex.getMessage());
            writeError(response, ex);
        } catch (RuntimeException ex) {
            // 查库等基础设施异常不能静默吞没，也不能把内部细节返回给客户端。
            LOG.error("authentication failed", ex);
            writeError(response, ChatCompletionException.internalError("鉴权失败", ex));
        } finally {
            // 线程池复用：请求结束必须清理租户上下文，避免串租户。
            TenantContextHolder.clear();
        }
    }

    /**
     * 为虚拟 Key 补充项目级授权列表（跨项目记忆/知识库共享）。
     *
     * @param auth 认证上下文
     * @return 补充授权项目后的上下文
     */
    private KeyAuthDTO enrich(KeyAuthDTO auth) {
        if (auth.masterKey() || !StringUtils.hasText(auth.tenantId())
                || !StringUtils.hasText(auth.projectId())) {
            return auth;
        }
        ProjectAuthorizationPort port = authorizationPortProvider.getIfAvailable();
        return KeyAuthDTO.virtualKey(auth.keyHash(), auth.models(), auth.rpmLimit(),
                auth.tpmLimit(), auth.maxBudget(), auth.budgetDuration(), auth.tenantId(),
                auth.projectId(), java.util.Objects.isNull(port) ? java.util.List.of()
                        : port.findAuthorizedProjects(auth.tenantId(), auth.projectId()));
    }

    /**
     * 解析并校验 Bearer token。
     *
     * @param request HTTP 请求
     * @return 认证上下文
     */
    private KeyAuthDTO authenticate(HttpServletRequest request) {
        String masterKey = properties.getMasterKey();
        if (!StringUtils.hasText(masterKey)) {
            // 本地开发模式：未配置 master key 时不启用鉴权。
            return KeyAuthDTO.master();
        }
        String token = bearerToken(request);
        if (!StringUtils.hasText(token)) {
            throw ChatCompletionException.invalidApiKey();
        }
        if (constantTimeEquals(masterKey, token)) {
            // master key 只属于控制面（console）；数据面一律拒绝。
            throw ChatCompletionException.adminKeyNotAllowed();
        }
        return keyService.authenticate(token);
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, ChatCompletionException ex) throws IOException {
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(ex.getMessage(), ex.getType(), ex.getParam(), ex.getCode()));
    }
}
