package com.qtua.aihub.console.controller;

import com.qtua.aihub.api.ConsoleSessionService;
import com.qtua.aihub.api.model.auth.ConsoleSessionAuthDTO;
import com.qtua.aihub.api.model.req.ConsoleLoginReqDTO;
import com.qtua.aihub.api.model.res.ConsoleSessionResDTO;
import com.qtua.aihub.common.config.ConsoleSessionProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Objects;

/**
 * 控制台会话端点：登录、会话探测、退出。
 *
 * <p>会话令牌只写 httpOnly cookie，响应体不返回令牌；cookie 限制在
 * {@code /v1/admin} 路径下，数据面请求不会携带。</p>
 */
@RestController
public class ConsoleSessionController {

    private static final String COOKIE_PATH = "/v1/admin";
    private static final String SAME_SITE = "Lax";

    private final ConsoleSessionService sessionService;
    private final ConsoleSessionProperties properties;

    /**
     * 构造控制器。
     *
     * @param sessionService 会话服务
     * @param properties     会话配置
     */
    public ConsoleSessionController(ConsoleSessionService sessionService,
                                    ConsoleSessionProperties properties) {
        this.sessionService = sessionService;
        this.properties = properties;
    }

    /**
     * 登录并下发会话 cookie。
     *
     * @param reqDTO 账号密码
     * @return 会话视图（不含令牌）
     */
    @PostMapping(path = "/v1/admin/session", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConsoleSessionResDTO> login(@RequestBody ConsoleLoginReqDTO reqDTO) {
        ConsoleSessionAuthDTO auth = sessionService.login(reqDTO);
        ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), auth.token())
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(properties.effectiveTimeout())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new ConsoleSessionResDTO(auth.username(), auth.expiresAt().toString()));
    }

    /**
     * 会话探测。
     *
     * @param request HTTP 请求
     * @return 当前会话；未登录返回 401
     */
    @GetMapping(path = "/v1/admin/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public ConsoleSessionResDTO current(HttpServletRequest request) {
        return sessionService.current(tokenOf(request));
    }

    /**
     * 退出登录并清除 cookie（幂等）。
     *
     * @param request HTTP 请求
     * @return 204
     */
    @DeleteMapping(path = "/v1/admin/session")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        sessionService.logout(tokenOf(request));
        ResponseCookie cleared = ResponseCookie.from(properties.getCookieName(), "")
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    private String tokenOf(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (Objects.isNull(cookies)) {
            return null;
        }
        String cookieName = properties.getCookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
