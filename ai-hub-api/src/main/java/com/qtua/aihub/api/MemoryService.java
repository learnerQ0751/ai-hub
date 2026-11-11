package com.qtua.aihub.api;

import com.qtua.aihub.api.model.req.MemoryClearReqDTO;
import com.qtua.aihub.api.model.req.MemoryRecallReqDTO;
import com.qtua.aihub.api.model.req.MemorySaveReqDTO;
import com.qtua.aihub.api.model.res.MemoryClearResDTO;
import com.qtua.aihub.api.model.res.MemoryRecallResDTO;
import com.qtua.aihub.api.model.res.MemorySaveResDTO;

/**
 * 记忆服务：session / user 两级记忆的保存、召回与清理。
 *
 * <p>所有操作强制携带 tenant_id + project_id + user_id；默认项目隔离，
 * 用户级记忆可配置为同租户跨项目共享。</p>
 */
public interface MemoryService {

    /**
     * 保存一条记忆。
     *
     * @param reqDTO 保存参数
     * @return 记忆 ID、类型与过期时间
     */
    MemorySaveResDTO save(MemorySaveReqDTO reqDTO);

    /**
     * 按作用域与关键词/时间范围召回未过期记忆。
     *
     * @param reqDTO 召回参数
     * @return 命中记忆；无命中返回空列表
     */
    MemoryRecallResDTO recall(MemoryRecallReqDTO reqDTO);

    /**
     * 按用户/会话物理删除记忆并写审计。
     *
     * @param reqDTO      清理参数
     * @param operatorKey 执行删除的虚拟 Key 哈希；master 为 null
     * @return 删除条数与审计 ID
     */
    MemoryClearResDTO clear(MemoryClearReqDTO reqDTO, String operatorKey);
}
