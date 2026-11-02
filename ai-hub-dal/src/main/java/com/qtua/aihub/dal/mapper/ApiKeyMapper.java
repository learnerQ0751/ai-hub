package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.ApiKeyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * `api_keys` 表 Mapper。
 *
 * <p>SQL 全部在 `mapper/ApiKeyMapper.xml`；本接口只声明数据访问方法。
 * `models` / `metadata` 为 PostgreSQL JSONB，写入时显式 CAST，读取时转成 VARCHAR。</p>
 */
@Mapper
public interface ApiKeyMapper {

    /**
     * 插入一条 Key 记录。
     *
     * @param apiKey 数据对象（key_hash 必须已存在）
     */
    void insert(ApiKeyDO apiKey);

    /**
     * 按哈希查询 Key。
     *
     * @param keyHash SHA-256 摘要
     * @return Key 记录；不存在时为空
     */
    Optional<ApiKeyDO> selectByKeyHash(@Param("keyHash") String keyHash);

    /**
     * 更新封禁状态。
     *
     * @param keyHash SHA-256 摘要
     * @param blocked 是否封禁
     * @return 受影响行数
     */
    int updateBlocked(@Param("keyHash") String keyHash, @Param("blocked") boolean blocked);

    /**
     * 原子累加消费金额。
     *
     * <p>使用 {@code spend = spend + ?} 由数据库保证并发安全，避免多实例
     * 「先查再写」丢失更新。</p>
     *
     * @param keyHash SHA-256 摘要
     * @param amount  本次成本（非负）
     * @return 受影响行数
     */
    int addSpend(@Param("keyHash") String keyHash, @Param("amount") BigDecimal amount);
}
