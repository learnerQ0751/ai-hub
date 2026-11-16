package com.qtua.aihub.common.tenant;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 当前请求的租户身份（由鉴权上下文派生，不来自请求参数）。
 *
 * <p>虚拟 Key 绑定 {@code (tenantId, projectId)}；授权项目来自 project 级授权表，
 * 用于记忆/知识库的显式跨项目共享。master key 视为管理身份，可显式指定租户。</p>
 *
 * @param keyHash            虚拟 Key 的 SHA-256；master 为 null
 * @param tenantId           Key 绑定的租户
 * @param projectId          Key 绑定的项目
 * @param authorizedProjects 显式授权可访问的项目（同租户）
 * @param master             是否为 master key
 */
public record TenantIdentity(String keyHash, String tenantId, String projectId,
                             Set<String> authorizedProjects, boolean master) {

    /**
     * 防御性复制授权项目集合。
     */
    public TenantIdentity {
        authorizedProjects = Objects.isNull(authorizedProjects)
                ? Set.of() : Set.copyOf(new LinkedHashSet<>(authorizedProjects));
    }

    /**
     * 构造虚拟 Key 身份。
     *
     * @param keyHash            虚拟 Key 哈希
     * @param tenantId           绑定租户
     * @param projectId          绑定项目
     * @param authorizedProjects 授权项目
     * @return 身份
     */
    public static TenantIdentity virtualKey(String keyHash, String tenantId, String projectId,
                                            List<String> authorizedProjects) {
        return new TenantIdentity(keyHash, tenantId, projectId, new LinkedHashSet<>(
                Objects.isNull(authorizedProjects) ? List.of() : authorizedProjects), false);
    }

    /**
     * master key 身份（全租户管理权限）。
     *
     * @return 身份
     */
    public static TenantIdentity masterIdentity() {
        return new TenantIdentity(null, null, null, Set.of(), true);
    }
}
