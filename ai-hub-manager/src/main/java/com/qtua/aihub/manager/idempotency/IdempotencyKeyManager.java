package com.qtua.aihub.manager.idempotency;

import com.qtua.aihub.dal.mapper.IdempotencyKeyMapper;
import com.qtua.aihub.dal.model.IdempotencyKeyDO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 幂等键数据门面（REQ-5.2）。
 *
 * <p>幂等键落库，重启后仍可重放首次响应；并发预占由唯一约束兜底。</p>
 */
@Component
public class IdempotencyKeyManager {

    private final IdempotencyKeyMapper mapper;

    /**
     * 构造门面。
     *
     * @param mapper 幂等键 Mapper
     */
    public IdempotencyKeyManager(IdempotencyKeyMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 预占幂等键。
     *
     * @param key         幂等键
     * @param requestHash 请求指纹
     * @return true 表示本次预占成功；false 表示键已存在
     */
    public boolean reserve(String key, String requestHash) {
        try {
            return mapper.insert(key, requestHash) > 0;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    /**
     * 查询幂等记录。
     *
     * @param key 幂等键
     * @return 记录
     */
    public Optional<Record> find(String key) {
        return mapper.selectByKey(key).map(this::toRecord);
    }

    /**
     * 回填首次响应。
     *
     * @param key         幂等键
     * @param status      状态码
     * @param contentType 响应类型
     * @param body        响应体
     */
    public void complete(String key, int status, String contentType, String body) {
        mapper.updateResponse(key, status, contentType, body);
    }

    /**
     * 释放幂等键（业务失败时允许客户端重试）。
     *
     * @param key 幂等键
     */
    public void release(String key) {
        mapper.deleteByKey(key);
    }

    private Record toRecord(IdempotencyKeyDO dataObject) {
        return new Record(dataObject.getRequestHash(), dataObject.getResponseStatus(),
                dataObject.getResponseContentType(), dataObject.getResponseBody());
    }

    /**
     * 幂等记录视图。
     *
     * @param requestHash       请求指纹
     * @param status            首次响应状态；null 表示处理中
     * @param contentType       首次响应类型
     * @param body              首次响应体
     */
    public record Record(String requestHash, Integer status, String contentType, String body) {
    }
}
