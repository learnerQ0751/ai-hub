package com.qtua.aihub.manager.session;

import com.qtua.aihub.common.util.KeyHasher;
import com.qtua.aihub.dal.mapper.AdminSessionMapper;
import com.qtua.aihub.dal.model.AdminSessionDO;
import com.qtua.aihub.manager.model.AdminSessionBO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 控制台会话数据门面。
 *
 * <p>生成高熵会话令牌，库中只保存 SHA-256 摘要；校验时按摘要查询并检查过期时间。</p>
 */
@Component
public class AdminSessionManager {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final AdminSessionMapper sessionMapper;

    /**
     * 构造门面。
     *
     * @param sessionMapper 会话 Mapper
     */
    public AdminSessionManager(AdminSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    /**
     * 创建会话。
     *
     * @param username 管理员账号
     * @param now      当前时间
     * @param timeout  有效期
     * @return 明文令牌与过期时间
     */
    public SessionTicket create(String username, Instant now, Duration timeout) {
        String token = generateToken();
        Instant expiresAt = now.plus(timeout);
        AdminSessionDO session = new AdminSessionDO();
        session.setTokenHash(KeyHasher.sha256Hex(token));
        session.setUsername(username);
        session.setCreatedAt(now);
        session.setExpiresAt(expiresAt);
        sessionMapper.insert(session);
        return new SessionTicket(token, expiresAt);
    }

    /**
     * 查询未过期的会话。
     *
     * @param rawToken 明文令牌；为空时直接返回空
     * @param now      当前时间
     * @return 会话记录；无效或过期时为空
     */
    public Optional<AdminSessionBO> findActive(String rawToken, Instant now) {
        if (!StringUtils.hasText(rawToken)) {
            return Optional.empty();
        }
        return sessionMapper.selectByTokenHash(KeyHasher.sha256Hex(rawToken))
                .filter(session -> now.isBefore(session.getExpiresAt()))
                .map(session -> new AdminSessionBO(session.getUsername(),
                        session.getExpiresAt()));
    }

    /**
     * 删除会话（退出）。
     *
     * @param rawToken 明文令牌；为空时忽略
     */
    public void delete(String rawToken) {
        if (StringUtils.hasText(rawToken)) {
            sessionMapper.deleteByTokenHash(KeyHasher.sha256Hex(rawToken));
        }
    }

    /**
     * 清理过期会话。
     *
     * @param now 当前时间
     * @return 删除行数
     */
    public int deleteExpired(Instant now) {
        return sessionMapper.deleteExpired(now);
    }

    private String generateToken() {
        byte[] buffer = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /**
     * 新建会话的结果。
     *
     * @param token     明文令牌（只写 cookie）
     * @param expiresAt 过期时间
     */
    public record SessionTicket(String token, Instant expiresAt) {
    }
}
