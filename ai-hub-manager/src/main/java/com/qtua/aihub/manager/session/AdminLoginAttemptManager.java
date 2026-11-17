package com.qtua.aihub.manager.session;

import com.qtua.aihub.dal.mapper.AdminLoginAttemptMapper;
import com.qtua.aihub.dal.model.AdminLoginAttemptDO;
import com.qtua.aihub.manager.model.AdminLoginAttemptBO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * 控制台登录失败计数门面。
 *
 * <p>计数落库，多实例共享；并发插入同一账号时由唯一约束兜底转更新。</p>
 */
@Component
public class AdminLoginAttemptManager {

    private final AdminLoginAttemptMapper attemptMapper;

    /**
     * 构造门面。
     *
     * @param attemptMapper 计数 Mapper
     */
    public AdminLoginAttemptManager(AdminLoginAttemptMapper attemptMapper) {
        this.attemptMapper = attemptMapper;
    }

    /**
     * 查询账号失败计数。
     *
     * @param username 管理员账号
     * @return 计数记录；不存在时为空
     */
    public Optional<AdminLoginAttemptBO> find(String username) {
        return attemptMapper.selectByUsername(username)
                .map(attempt -> new AdminLoginAttemptBO(attempt.getUsername(),
                        attempt.getFailedCount(), attempt.getLockedUntil()));
    }

    /**
     * 保存失败计数（不存在时插入，存在时更新）。
     *
     * @param attempt 计数记录
     * @param now     更新时间
     */
    public void save(AdminLoginAttemptBO attempt, Instant now) {
        AdminLoginAttemptDO dataObject = new AdminLoginAttemptDO();
        dataObject.setUsername(attempt.username());
        dataObject.setFailedCount(attempt.failedCount());
        dataObject.setLockedUntil(attempt.lockedUntil());
        dataObject.setUpdatedAt(now);
        if (attemptMapper.update(dataObject) > 0) {
            return;
        }
        try {
            attemptMapper.insert(dataObject);
        } catch (DuplicateKeyException ex) {
            // 并发首次失败：唯一约束兜底，转为更新。
            attemptMapper.update(dataObject);
        }
    }

    /**
     * 清除失败计数。
     *
     * @param username 管理员账号
     * @return 受影响行数
     */
    public int clear(String username) {
        return attemptMapper.deleteByUsername(username);
    }
}
