package com.qtua.aihub.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.ErrorResponse;
import com.qtua.aihub.config.GatewayProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 轻量鉴权过滤器（master key 占位）。
 *
 * <p>配置了 gateway.master-key 时要求 Authorization: Bearer &lt;master-key&gt;；
 * 未配置时放行，便于本地开发。虚拟 Key 在后续需求实现。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 构造过滤器。
     *
     * @param properties   网关配置
     * @param objectMapper JSON 序列化器（用于写错误体）
     */
    public AuthFilter(GatewayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验 Bearer token；失败时直接返回 OpenAI 兼容 401 错误体。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (StringUtils.hasText(properties.getMasterKey()) && !isAuthorized(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    ErrorResponse.of("Authentication Error", "auth_error", null, "401"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 判断请求是否携带正确的 master key。
     *
     * @param request HTTP 请求
     * @return 是否通过鉴权
     */
    private boolean isAuthorized(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return StringUtils.hasText(authorization)
                && authorization.equals(BEARER_PREFIX + properties.getMasterKey());
    }
}
