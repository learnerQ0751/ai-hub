package com.qtua.aihub.manager.model;

import java.time.Instant;

/**
 * 项目业务对象（§7.1 项目列表）。
 *
 * @param tenantId  租户
 * @param projectId 项目
 * @param name      展示名
 * @param status    状态
 * @param createdAt 创建时间
 */
public record ProjectBO(String tenantId, String projectId, String name, String status,
                        Instant createdAt) {
}
