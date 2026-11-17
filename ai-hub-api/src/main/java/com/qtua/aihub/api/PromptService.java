package com.qtua.aihub.api;

import com.qtua.aihub.api.model.req.PromptCreateReqDTO;
import com.qtua.aihub.api.model.req.PromptVersionCreateReqDTO;
import com.qtua.aihub.api.model.res.PromptListResDTO;
import com.qtua.aihub.api.model.res.PromptResDTO;
import com.qtua.aihub.api.model.res.PromptVersionListResDTO;

/**
 * Prompt 模板管理服务（管理面，仅 master key）。
 *
 * <p>版本只追加；发布/回滚通过 publish 指定版本完成（同一 name 仅一个 PUBLISHED）。</p>
 */
public interface PromptService {

    /**
     * 创建模板 v1（DRAFT）。
     *
     * @param reqDTO 创建参数
     * @return 创建的版本
     */
    PromptResDTO create(PromptCreateReqDTO reqDTO);

    /**
     * 追加新版本（DRAFT）。
     *
     * @param name   模板名
     * @param reqDTO 版本参数
     * @return 新版本
     */
    PromptResDTO createVersion(String name, PromptVersionCreateReqDTO reqDTO);

    /**
     * 查询模板。
     *
     * @param name     模板名
     * @param version  版本（null 表示最新发布版）
     * @param tenantId 租户（可空，为空取当前作用域）
     * @return 版本视图
     */
    PromptResDTO get(String name, Integer version, String tenantId);

    /**
     * 版本历史。
     *
     * @param name     模板名
     * @param tenantId 租户（可空，为空取当前作用域）
     * @return 版本列表
     */
    PromptVersionListResDTO versions(String name, String tenantId);

    /**
     * 模板列表（每个模板的最新发布版本）。
     *
     * @param tenantId 租户过滤（可空）
     * @return 模板列表
     */
    PromptListResDTO list(String tenantId);

    /**
     * 发布指定版本（发布旧版本即回滚）。
     *
     * @param name     模板名
     * @param version  目标版本
     * @param tenantId 租户（可空，为空取当前作用域）
     * @return 发布后的版本
     */
    PromptResDTO publish(String name, int version, String tenantId);
}
