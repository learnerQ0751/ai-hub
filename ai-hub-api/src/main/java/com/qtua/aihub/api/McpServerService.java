package com.qtua.aihub.api;

import com.qtua.aihub.api.model.req.McpServerRegisterReqDTO;
import com.qtua.aihub.api.model.res.McpServerResDTO;

import java.util.List;

/**
 * MCP Server 注册与发现服务（管理面，仅 master key）。
 *
 * <p>注册时同步探活（MCP initialize）；探活失败不阻断注册，状态置 DOWN 并记录错误，
 * 可通过 {@link #reprobe(String)} 手动重试。</p>
 */
public interface McpServerService {

    /**
     * 注册 MCP Server。
     *
     * @param reqDTO 注册参数
     * @return 注册结果（含探活后的状态）
     */
    McpServerResDTO register(McpServerRegisterReqDTO reqDTO);

    /**
     * 查询全部 MCP Server。
     *
     * @return 服务器列表（endpoint 已掩码）
     */
    List<McpServerResDTO> list();

    /**
     * 注销 MCP Server。
     *
     * @param serverId 对外服务器 ID
     */
    void remove(String serverId);

    /**
     * 手动重新探活。
     *
     * @param serverId 对外服务器 ID
     * @return 探活后的最新视图
     */
    McpServerResDTO reprobe(String serverId);
}
