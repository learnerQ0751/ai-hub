package com.qtua.aihub.console.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.ConsoleSessionService;
import com.qtua.aihub.api.model.res.ErrorResponse;
import com.qtua.aihub.common.config.ConsoleSessionProperties;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.tenant.TenantContextHolder;
import com.qtua.aihub.common.tenant.TenantIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 控制面鉴权过滤器（REQ-5.1）。
 *
 * <p>只认 master key（admin 凭证）或控制台会话 cookie：console 不加载虚拟 Key
 * 业务鉴权链，避免管理接口复用业务鉴权导致越权。虚拟 Key 或任意非 master 凭证一律 403。</p>
 *
 * <p>安全约束：不记录明文 Key 或会话令牌；常量时间比较；请求结束清理租户上下文。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AdminAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SESSION_PATH = "/v1/admin/session";

    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final ConsoleSessionService sessionService;
    private final ConsoleSessionProperties sessionProperties;

    /**
     * 构造过滤器。
     *
     * @param properties        网关配置（master key 来源）
     * @param objectMapper      JSON 序列化器（错误响应）
     * @param sessionService    控制台会话服务
     * @param sessionProperties 会话配置（cookie 名）
     */
    public AdminAuthFilter(GatewayProperties properties, ObjectMapper objectMapper,
                           ConsoleSessionService sessionService,
                           ConsoleSessionProperties sessionProperties) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.sessionProperties = sessionProperties;
    }

    /**
     * 健康检查端点不做鉴权；会话端点自行校验登录态（登录必须匿名可达）。
     *
     * @param request HTTP 请求
     * @return true 表示跳过鉴权
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || SESSION_PATH.equals(uri)
                || (SESSION_PATH + "/").equals(uri);
    }

    /**
     * 校验 admin 凭证并写入租户上下文。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String masterKey = properties.getMasterKey();
            if (!StringUtils.hasText(masterKey)) {
                // 本地开发模式：未配置 master key 时放行（仅限本地）。
                TenantContextHolder.set(TenantIdentity.masterIdentity());
                filterChain.doFilter(request, response);
                return;
            }
            String token = bearerToken(request);
            if (StringUtils.hasText(token)) {
                if (!constantTimeEquals(masterKey, token)) {
                    throw ChatCompletionException.adminRequired();
                }
            } else if (!sessionService.isActive(sessionToken(request))) {
                throw ChatCompletionException.invalidApiKey();
            }
            TenantContextHolder.set(TenantIdentity.masterIdentity());
            filterChain.doFilter(request, response);
        } catch (ChatCompletionException ex) {
            LOG.warn("admin authentication rejected: type={}, message={}",
                    ex.getType(), ex.getMessage());
            writeError(response, ex);
        } catch (RuntimeException ex) {
            LOG.error("admin authentication failed", ex);
            writeError(response, ChatCompletionException.internalError("鉴权失败", ex));
        } finally {
            TenantContextHolder.clear();
        }
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private String sessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (ObjectUtils.isEmpty(cookies)) {
            return null;
        }
        String cookieName = sessionProperties.getCookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, ChatCompletionException ex)
            throws IOException {
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(ex.getMessage(), ex.getType(), ex.getParam(), ex.getCode()));
    }
}
