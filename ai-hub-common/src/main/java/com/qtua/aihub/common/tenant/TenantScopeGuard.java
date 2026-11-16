package com.qtua.aihub.common.tenant;

import com.qtua.aihub.common.exception.ChatCompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * 租户作用域解析与越权拦截。
 *
 * <p>硬约束：{@code tenant_id/project_id} 以 Key 绑定为准，请求参数只能作为
 * 「显式声明」被校验，不能覆盖绑定；不一致立即 403 并记录告警日志。
 * 未设置上下文时（单元测试/内部调用）沿用请求参数，入口过滤器在生产链路中
 * 始终会设置上下文。</p>
 */
@Component
public class TenantScopeGuard {

    /** 未显式绑定租户时的默认租户（本地/兼容数据）。 */
    public static final String DEFAULT_TENANT = "default";
    /** 未指定项目时的默认项目。 */
    public static final String DEFAULT_PROJECT = "default";

    private static final Logger LOG = LoggerFactory.getLogger(TenantScopeGuard.class);

    /**
     * 解析生效作用域并校验权限。
     *
     * @param requestedTenant  请求声明的租户（可空）
     * @param requestedProject 请求声明的项目（可空）
     * @return 生效作用域
     */
    public TenantScope resolve(String requestedTenant, String requestedProject) {
        String tenant = trimToNull(requestedTenant);
        String project = trimToNull(requestedProject);
        TenantIdentity identity = TenantContextHolder.current();
        if (ObjectUtils.isEmpty(identity)) {
            // 无入口上下文（单元测试/内部调用）：请求必须显式声明租户与项目。
            requireText(tenant, "tenant_id");
            requireText(project, "project_id");
            return new TenantScope(tenant, project, false);
        }
        if (identity.master()) {
            return new TenantScope(!ObjectUtils.isEmpty(tenant) ? tenant : DEFAULT_TENANT,
                    !ObjectUtils.isEmpty(project) ? project : DEFAULT_PROJECT, true);
        }
        String boundTenant = trimToNull(identity.tenantId());
        String boundProject = trimToNull(identity.projectId());
        if (ObjectUtils.isEmpty(boundTenant)) {
            deny(identity, tenant, project, "当前 API Key 未绑定租户，禁止访问租户资源");
        }
        if (!ObjectUtils.isEmpty(tenant) && !tenant.equals(boundTenant)) {
            deny(identity, tenant, project, "请求租户与 API Key 绑定租户不一致");
        }
        if (!ObjectUtils.isEmpty(project) && !project.equals(boundProject)
                && !identity.authorizedProjects().contains(project)) {
            deny(identity, tenant, project, "请求项目超出 API Key 授权范围");
        }
        String effectiveProject = !ObjectUtils.isEmpty(project) ? project
                : (!ObjectUtils.isEmpty(boundProject) ? boundProject : DEFAULT_PROJECT);
        return new TenantScope(boundTenant, effectiveProject, false);
    }

    /**
     * 解析「租户来自 Key、项目来自请求或 Key 绑定」的作用域（RAG 等资源）。
     *
     * @param requestedProject 请求声明的项目（可空；虚拟 Key 缺省用绑定项目）
     * @return 生效作用域
     */
    public TenantScope resolveProject(String requestedProject) {
        String project = trimToNull(requestedProject);
        TenantIdentity identity = TenantContextHolder.current();
        if (ObjectUtils.isEmpty(identity)) {
            if (ObjectUtils.isEmpty(project)) {
                throw ChatCompletionException.invalidRequest("project_id 不能为空", "project_id");
            }
            return new TenantScope(DEFAULT_TENANT, project, false);
        }
        if (identity.master()) {
            if (ObjectUtils.isEmpty(project)) {
                throw ChatCompletionException.invalidRequest("project_id 不能为空", "project_id");
            }
            return new TenantScope(DEFAULT_TENANT, project, true);
        }
        String boundTenant = trimToNull(identity.tenantId());
        String boundProject = trimToNull(identity.projectId());
        if (ObjectUtils.isEmpty(boundTenant)) {
            deny(identity, null, project, "当前 API Key 未绑定租户，禁止访问租户资源");
        }
        if (!ObjectUtils.isEmpty(project) && !project.equals(boundProject)
                && !identity.authorizedProjects().contains(project)) {
            deny(identity, null, project, "请求项目超出 API Key 授权范围");
        }
        String effectiveProject = !ObjectUtils.isEmpty(project) ? project
                : (!ObjectUtils.isEmpty(boundProject) ? boundProject : DEFAULT_PROJECT);
        return new TenantScope(boundTenant, effectiveProject, false);
    }

    /**
     * 仅解析租户（用于跨项目列表等只需要租户边界的场景）。
     *
     * @param requestedTenant 请求声明的租户（可空）
     * @return 生效租户
     */
    public String resolveTenant(String requestedTenant) {
        TenantIdentity identity = TenantContextHolder.current();
        String tenant = trimToNull(requestedTenant);
        if (ObjectUtils.isEmpty(identity)) {
            requireText(tenant, "tenant_id");
            return tenant;
        }
        if (identity.master()) {
            return !ObjectUtils.isEmpty(tenant) ? tenant : DEFAULT_TENANT;
        }
        String boundTenant = trimToNull(identity.tenantId());
        if (ObjectUtils.isEmpty(boundTenant)) {
            deny(identity, tenant, null, "当前 API Key 未绑定租户，禁止访问租户资源");
        }
        if (!ObjectUtils.isEmpty(tenant) && !tenant.equals(boundTenant)) {
            deny(identity, tenant, null, "请求租户与 API Key 绑定租户不一致");
        }
        return boundTenant;
    }

    private void requireText(String value, String param) {
        if (!StringUtils.hasText(value)) {
            throw ChatCompletionException.invalidRequest(param + " 不能为空", param);
        }
    }

    /**
     * 获取当前认证身份。
     *
     * @return 身份；未设置时为 null
     */
    public TenantIdentity currentIdentity() {
        return TenantContextHolder.current();
    }

    /**
     * 要求 master 身份（管理面）。
     */
    public void requireMaster() {
        TenantIdentity identity = TenantContextHolder.current();
        if (ObjectUtils.isEmpty(identity) || !identity.master()) {
            throw ChatCompletionException.adminRequired();
        }
    }

    private void deny(TenantIdentity identity, String tenant, String project, String reason) {
        LOG.warn("tenant access denied: keyHash={}, requestTenant={}, requestProject={}, "
                        + "boundTenant={}, boundProject={}, reason={}",
                identity.keyHash(), tenant, project, identity.tenantId(), identity.projectId(),
                reason);
        throw ChatCompletionException.tenantAccessDenied(reason, tenant, project);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
