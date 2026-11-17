package com.qtua.aihub.service.console;

import com.qtua.aihub.api.ConsoleSessionService;
import com.qtua.aihub.api.model.auth.ConsoleSessionAuthDTO;
import com.qtua.aihub.api.model.req.ConsoleLoginReqDTO;
import com.qtua.aihub.api.model.res.ConsoleSessionResDTO;
import com.qtua.aihub.common.config.ConsoleSessionProperties;
import com.qtua.aihub.common.crypto.Pbkdf2PasswordHasher;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.manager.model.AdminLoginAttemptBO;
import com.qtua.aihub.manager.model.AdminSessionBO;
import com.qtua.aihub.manager.session.AdminLoginAttemptManager;
import com.qtua.aihub.manager.session.AdminSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;

/**
 * 控制台管理员会话实现。
 *
 * <p>密码只以 PBKDF2 哈希形式存在配置中；连续失败达到阈值后锁定，
 * 失败计数与会话都落库，多实例一致且重启不丢失。</p>
 */
@Service
public class ConsoleSessionServiceImpl implements ConsoleSessionService {

    private static final Logger LOG = LoggerFactory.getLogger(ConsoleSessionServiceImpl.class);
    private static final int MAX_USERNAME_LENGTH = 128;

    private final ConsoleSessionProperties properties;
    private final AdminSessionManager sessionManager;
    private final AdminLoginAttemptManager attemptManager;

    /**
     * 构造服务。
     *
     * @param properties     会话配置
     * @param sessionManager 会话门面
     * @param attemptManager 登录失败计数门面
     */
    public ConsoleSessionServiceImpl(ConsoleSessionProperties properties,
                                     AdminSessionManager sessionManager,
                                     AdminLoginAttemptManager attemptManager) {
        this.properties = properties;
        this.sessionManager = sessionManager;
        this.attemptManager = attemptManager;
    }

    @Override
    public ConsoleSessionAuthDTO login(ConsoleLoginReqDTO reqDTO) {
        if (!configured()) {
            throw ChatCompletionException.consoleSessionDisabled();
        }
        String username = requireUsername(reqDTO.username());
        String password = requirePassword(reqDTO.password());
        Instant now = Instant.now();

        AdminLoginAttemptBO attempt = attemptManager.find(username).orElse(null);
        if (Objects.nonNull(attempt) && Objects.nonNull(attempt.lockedUntil())
                && now.isBefore(attempt.lockedUntil())) {
            throw ChatCompletionException.consoleLoginLocked();
        }
        boolean usernameMatches = constantTimeEquals(properties.getUsername(), username);
        boolean passwordMatches = Pbkdf2PasswordHasher.matches(password,
                properties.getPasswordHash());
        // 按位与：两项都校验，避免账号是否存在影响耗时。
        if (!(usernameMatches & passwordMatches)) {
            recordFailure(username, attempt, now);
        }
        attemptManager.clear(username);
        sessionManager.deleteExpired(now);
        AdminSessionManager.SessionTicket ticket = sessionManager.create(username, now,
                properties.effectiveTimeout());
        LOG.info("console session created: username={}, expiresAt={}", username,
                ticket.expiresAt());
        return new ConsoleSessionAuthDTO(username, ticket.token(), ticket.expiresAt());
    }

    @Override
    public ConsoleSessionResDTO current(String token) {
        AdminSessionBO session = requireActive(token);
        return new ConsoleSessionResDTO(session.username(), session.expiresAt().toString());
    }

    @Override
    public boolean isActive(String token) {
        return properties.isEnabled()
                && sessionManager.findActive(token, Instant.now()).isPresent();
    }

    @Override
    public void logout(String token) {
        sessionManager.delete(token);
    }

    private AdminSessionBO requireActive(String token) {
        return sessionManager.findActive(token, Instant.now())
                .orElseThrow(ChatCompletionException::consoleSessionRequired);
    }

    private void recordFailure(String username, AdminLoginAttemptBO existing, Instant now) {
        int failedCount = (Objects.isNull(existing) ? 0 : existing.failedCount()) + 1;
        boolean locked = failedCount >= properties.effectiveMaxFailedAttempts();
        Instant lockedUntil = locked ? now.plus(properties.effectiveLockDuration()) : null;

        attemptManager.save(new AdminLoginAttemptBO(username, failedCount, lockedUntil), now);
        LOG.warn("console login failed: username={}, failures={}, locked={}", username,
                failedCount, locked);
        if (locked) {
            throw ChatCompletionException.consoleLoginLocked();
        }
        throw ChatCompletionException.consoleLoginFailed();
    }

    private boolean configured() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getUsername())
                && StringUtils.hasText(properties.getPasswordHash());
    }

    private String requireUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw ChatCompletionException.invalidRequest("username 不能为空", "username");
        }
        String trimmed = username.trim();
        if (trimmed.length() > MAX_USERNAME_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    "username 长度不能超过 " + MAX_USERNAME_LENGTH, "username");
        }
        return trimmed;
    }

    private String requirePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw ChatCompletionException.invalidRequest("password 不能为空", "password");
        }
        return password;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
