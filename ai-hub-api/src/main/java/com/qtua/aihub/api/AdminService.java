package com.qtua.aihub.api;

import com.qtua.aihub.api.model.req.AliasUpsertReqDTO;
import com.qtua.aihub.api.model.req.DeploymentUpsertReqDTO;
import com.qtua.aihub.api.model.req.ProjectAuthorizationReqDTO;
import com.qtua.aihub.api.model.res.AdminKeyPageResDTO;
import com.qtua.aihub.api.model.res.AdminKeyResDTO;
import com.qtua.aihub.api.model.res.AdminLogPageResDTO;
import com.qtua.aihub.api.model.res.AdminModelResDTO;

import java.util.List;

/**
 * 管理端 API（REQ-4.4）。
 *
 * <p>所有方法只由 master key 调用（入口过滤器已拦截）；返回值不得包含明文 Key
 * 或上游 api-key。</p>
 */
public interface AdminService {

    /**
     * Key 分页列表。
     *
     * @param tenantId  租户（可空）
     * @param projectId 项目（可空）
     * @param status    状态：active / blocked / expired（可空）
     * @param model     模型白名单包含的别名（可空）
     * @param limit     每页条数
     * @param offset    偏移
     * @return 分页
     */
    AdminKeyPageResDTO listKeys(String tenantId, String projectId, String status, String model,
                                int limit, int offset);

    /**
     * 封禁/解封 Key（按哈希）。
     *
     * @param keyHash Key 哈希
     * @param blocked 是否封禁
     * @return 更新后的视图
     */
    AdminKeyResDTO setKeyBlocked(String keyHash, boolean blocked);

    /**
     * 模型别名列表（含部署状态）。
     *
     * @param tenantId 租户过滤（可空）
     * @param status   状态过滤 ACTIVE/DISABLED（可空）
     * @return 别名列表
     */
    List<AdminModelResDTO> listModels(String tenantId, String status);

    /**
     * 查询单个别名。
     *
     * @param alias 别名
     * @return 别名视图
     */
    AdminModelResDTO getModel(String alias);

    /**
     * 创建别名（可选带部署）。
     *
     * @param reqDTO 参数
     * @return 别名视图
     */
    AdminModelResDTO createModel(AliasUpsertReqDTO reqDTO);

    /**
     * 更新别名描述/降级链/状态。
     *
     * @param alias  别名
     * @param reqDTO 参数
     * @return 别名视图
     */
    AdminModelResDTO updateModel(String alias, AliasUpsertReqDTO reqDTO);

    /**
     * 删除别名及其关联（不删部署实例，可复用）。
     *
     * @param alias 别名
     */
    void deleteModel(String alias);

    /**
     * 为别名新增/更新部署实例。
     *
     * @param alias  别名
     * @param reqDTO 部署参数
     * @return 别名视图
     */
    AdminModelResDTO upsertDeployment(String alias, DeploymentUpsertReqDTO reqDTO);

    /**
     * 日志查询（分页）。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @param model     模型别名
     * @param keyId     Key 哈希
     * @param status    HTTP 状态
     * @param from      起始时间（ISO-8601，可空）
     * @param to        结束时间（ISO-8601，可空）
     * @param limit     每页条数
     * @param offset    偏移
     * @return 分页
     */
    AdminLogPageResDTO listLogs(String tenantId, String projectId, String model, String keyId,
                                Integer status, String from, String to, int limit, int offset);

    /**
     * 新增项目授权。
     *
     * @param reqDTO 授权参数
     */
    void authorizeProject(ProjectAuthorizationReqDTO reqDTO);
}
