package com.qtua.aihub.console.controller;

import com.qtua.aihub.api.AdminService;
import com.qtua.aihub.api.model.req.AliasUpsertReqDTO;
import com.qtua.aihub.api.model.req.DeploymentUpsertReqDTO;
import com.qtua.aihub.api.model.req.ProjectAuthorizationReqDTO;
import com.qtua.aihub.api.model.res.AdminKeyPageResDTO;
import com.qtua.aihub.api.model.res.AdminKeyResDTO;
import com.qtua.aihub.api.model.res.AdminLogPageResDTO;
import com.qtua.aihub.api.model.res.AdminModelResDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端入口（REQ-4.4）。
 *
 * <p>所有 `/v1/admin/**` 仅 master key 可访问（AuthFilter 对虚拟 Key 返回 403）；
 * 响应不包含明文 Key 与上游 api-key。</p>
 */
@RestController
public class AdminController {

    private final AdminService adminService;

    /**
     * 构造控制器。
     *
     * @param adminService 管理服务
     */
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Key 列表（脱敏）。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @param status    状态：active / blocked / expired
     * @param model     模型白名单包含的别名
     * @param limit     每页条数
     * @param offset    偏移
     * @return 分页
     */
    @GetMapping(path = "/v1/admin/keys", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminKeyPageResDTO listKeys(
            @RequestParam(name = "tenant_id", required = false) String tenantId,
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "model", required = false) String model,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return adminService.listKeys(tenantId, projectId, status, model, limit, offset);
    }

    /**
     * 封禁 Key。
     *
     * @param keyHash Key 哈希
     * @return 更新后的视图
     */
    @PostMapping(path = "/v1/admin/keys/{keyHash}/block",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminKeyResDTO block(@PathVariable("keyHash") String keyHash) {
        return adminService.setKeyBlocked(keyHash, true);
    }

    /**
     * 解封 Key。
     *
     * @param keyHash Key 哈希
     * @return 更新后的视图
     */
    @PostMapping(path = "/v1/admin/keys/{keyHash}/unblock",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminKeyResDTO unblock(@PathVariable("keyHash") String keyHash) {
        return adminService.setKeyBlocked(keyHash, false);
    }

    /**
     * 模型别名与部署状态。
     *
     * @param tenantId 租户过滤
     * @param status   状态过滤 ACTIVE/DISABLED
     * @return 别名列表
     */
    @GetMapping(path = "/v1/admin/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AdminModelResDTO> listModels(
            @RequestParam(name = "tenant_id", required = false) String tenantId,
            @RequestParam(name = "status", required = false) String status) {
        return adminService.listModels(tenantId, status);
    }

    /**
     * 查询单个别名。
     *
     * @param alias 别名
     * @return 别名视图
     */
    @GetMapping(path = "/v1/admin/models/{alias}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminModelResDTO getModel(@PathVariable("alias") String alias) {
        return adminService.getModel(alias);
    }

    /**
     * 创建别名。
     *
     * @param reqDTO 参数
     * @return 别名视图
     */
    @PostMapping(path = "/v1/admin/models", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminModelResDTO createModel(@RequestBody AliasUpsertReqDTO reqDTO) {
        return adminService.createModel(reqDTO);
    }

    /**
     * 更新别名。
     *
     * @param alias  别名
     * @param reqDTO 参数
     * @return 别名视图
     */
    @PutMapping(path = "/v1/admin/models/{alias}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminModelResDTO updateModel(@PathVariable("alias") String alias,
                                        @RequestBody AliasUpsertReqDTO reqDTO) {
        return adminService.updateModel(alias, reqDTO);
    }

    /**
     * 删除别名。
     *
     * @param alias 别名
     */
    @DeleteMapping(path = "/v1/admin/models/{alias}")
    public void deleteModel(@PathVariable("alias") String alias) {
        adminService.deleteModel(alias);
    }

    /**
     * 新增/更新部署实例。
     *
     * @param alias  别名
     * @param reqDTO 部署参数
     * @return 别名视图
     */
    @PostMapping(path = "/v1/admin/models/{alias}/deployments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminModelResDTO upsertDeployment(@PathVariable("alias") String alias,
                                             @RequestBody DeploymentUpsertReqDTO reqDTO) {
        return adminService.upsertDeployment(alias, reqDTO);
    }

    /**
     * 调用日志查询。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @param model     模型别名
     * @param keyId     Key 哈希
     * @param status    HTTP 状态
     * @param from      起始时间
     * @param to        结束时间
     * @param limit     每页条数
     * @param offset    偏移
     * @return 分页
     */
    @GetMapping(path = "/v1/admin/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminLogPageResDTO listLogs(
            @RequestParam(name = "tenant_id", required = false) String tenantId,
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "model", required = false) String model,
            @RequestParam(name = "key_id", required = false) String keyId,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return adminService.listLogs(tenantId, projectId, model, keyId, status, from, to,
                limit, offset);
    }

    /**
     * 新增项目授权。
     *
     * @param reqDTO 授权参数
     */
    @PostMapping(path = "/v1/admin/project-authorizations",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public void authorize(@RequestBody ProjectAuthorizationReqDTO reqDTO) {
        adminService.authorizeProject(reqDTO);
    }
}
