package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.McpServerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * `mcp_servers` 表 Mapper。
 *
 * <p>SQL 全部在 `mapper/McpServerMapper.xml`；本接口只声明数据访问方法。</p>
 */
@Mapper
public interface McpServerMapper {

    /**
     * 插入一条 MCP Server 记录。
     *
     * @param server 数据对象（server_id 必须已生成）
     */
    void insert(McpServerDO server);

    /**
     * 按 server_id 查询。
     *
     * @param serverId 对外服务器 ID
     * @return 记录；不存在时为空
     */
    Optional<McpServerDO> selectByServerId(@Param("serverId") String serverId);

    /**
     * 按名称查询（注册查重用）。
     *
     * @param name 服务器名
     * @return 记录；不存在时为空
     */
    Optional<McpServerDO> selectByName(@Param("name") String name);

    /**
     * 查询全部服务器（按创建时间倒序）。
     *
     * @return 服务器列表
     */
    List<McpServerDO> selectAll();

    /**
     * 查询状态为 ACTIVE 的服务器（启动加载用）。
     *
     * @return 活跃服务器列表
     */
    List<McpServerDO> selectActive();

    /**
     * 更新健康状态与探活信息。
     *
     * @param serverId     对外服务器 ID
     * @param status       ACTIVE/DOWN
     * @param lastHealthAt 本次探活时间
     * @param metadata     更新后的元信息 JSON（含 lastError）
     * @return 受影响行数
     */
    int updateHealth(@Param("serverId") String serverId,
                     @Param("status") String status,
                     @Param("lastHealthAt") Instant lastHealthAt,
                     @Param("metadata") String metadata);

    /**
     * 按 server_id 删除。
     *
     * @param serverId 对外服务器 ID
     * @return 受影响行数
     */
    int deleteByServerId(@Param("serverId") String serverId);
}
