package com.qtua.aihub.api;

import com.qtua.aihub.api.model.res.McpToolListResDTO;
import com.qtua.aihub.common.mcp.McpToolDescriptor;

import java.util.Optional;

/**
 * MCP 工具发现服务（数据面）。
 *
 * <p>聚合所有 ACTIVE Server 的工具，TTL 60s 本地快照；单个 Server 失败降级不阻塞。</p>
 */
public interface McpToolService {

    /**
     * 查询工具目录。
     *
     * @param serverIdOrName Server ID 或名称（可空，空表示全部）
     * @param tenantId       租户 ID（可空）
     * @return 工具目录（含降级信息）
     */
    McpToolListResDTO list(String serverIdOrName, String tenantId);

    /**
     * 按对外工具名查找（REQ-2.3 调用路由使用）。
     *
     * @param exposedName 对外工具名（server__tool）
     * @return 工具描述；不存在时为空
     */
    Optional<McpToolDescriptor> find(String exposedName);

    /**
     * 手动刷新工具目录（忽略 TTL）。
     */
    void refresh();
}
