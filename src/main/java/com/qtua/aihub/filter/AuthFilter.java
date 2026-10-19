package com.qtua.aihub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.config.GatewayProperties;
import com.qtua.aihub.gateway.ErrorResponse;
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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    public AuthFilter(GatewayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

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

    private boolean isAuthorized(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return StringUtils.hasText(authorization)
                && authorization.equals(BEARER_PREFIX + properties.getMasterKey());
    }
}
