# REQ-4.4：管理端（模型/Key/Prompt/MCP/日志）

- 日期：2026-11-16
- 状态：已完成（待主人验收）
- 关联规格：`13-项目7-第4期-治理收尾.md` REQ-4.4
- 依赖需求：REQ-4.1、REQ-4.3、REQ-4.5（审计在 4.5 接入）
- 提交：f333e7d

## 1. 是什么

新增 `/v1/admin/**` 管理 API，全部仅 master key 可访问：

- Key 列表（按租户/项目过滤、分页）、按哈希封禁/解封；
- 模型别名列表（含部署状态、缺省不返回上游 api-key）、别名 CRUD、部署实例新增/更新；
- 调用日志查询（租户/项目/模型/Key/状态/时间 + 分页）；
- 项目级授权新增（跨项目记忆/知识库）；
- Prompt 发布/回滚与 MCP Server 管理复用既有 master-only 接口（`/v1/prompts/*`、`/v1/mcp/servers`）。

## 2. 有什么用

- 治理能力要可操作：没有管理 API，租户/Key/模型/授权只能改库或改 YAML。
- 对应参照行为：LiteLLM 的 Keys / Models / Logs / Teams 页面；本期以最小 API 为主，页面不做。
- 面试叙事点：管理面与数据面鉴权分离、脱敏边界（api-key 永不回显、Key 哈希可作标识）、
  分页、审计与业务动作的一致性、配置 DB 化后的即时生效。

## 3. 怎么实现的

- 分层：
  - `start/controller/AdminController`：`/v1/admin/keys|models|logs|project-authorizations`
  - `service/AdminServiceImpl`：参数校验、分页收敛（默认 50/上限 200）、脱敏、审计
  - `manager`：ApiKeyManager.list、CallLogManager.findPage、ModelCatalogManager 管理方法、
    ProjectAuthorizationManager.authorize
  - `dal`：`ApiKeyMapper.selectPage`、`CallLogMapper.selectPage`、`ModelCatalogMapper` 管理查询
- 鉴权：AuthFilter 对 `/v1/admin*`、`/v1/audit-logs` 的虚拟 Key 返回 403 `authorization_error`。
- 脱敏：响应不包含明文 Key；模型部署只返回 `deployment_key/api_base/upstream_model/weight/
  priority/status`，`api_key_cipher` 永不返回；日志中的 `key_id` 是 Key 哈希（不可逆，用于关联）。
- 分页：`limit` 默认 50、上限 200；`offset` 非负；每页多取 1 条计算 `has_more`。
- 审计：Key 封禁/解封、别名 CRUD、部署更新、项目授权写 `audit_logs`（REQ-4.5）。

## 4. 影响面

```yaml
capability:
  type: cross-cutting
  entry_point: "AdminController + AuthFilter（master-only）"
  interface_agnostic: true
modules:
  add: [AdminController, AdminService/Impl, 管理 DTO, ModelAliasAdminBO, ModelDeploymentAdminBO]
  change: [ApiKeyMapper/XML, CallLogMapper/XML, ModelCatalogMapper/XML, ModelCatalogManager,
           CallLogManager, ApiKeyManager, AuthFilter(admin 403)]
interfaces:
  add:
    - "GET /v1/admin/keys"
    - "POST /v1/admin/keys/{keyHash}/block|unblock"
    - "GET/POST /v1/admin/models"
    - "GET/PUT/DELETE /v1/admin/models/{alias}"
    - "POST /v1/admin/models/{alias}/deployments"
    - "GET /v1/admin/logs"
    - "POST /v1/admin/project-authorizations"
  change: []
tables:
  add: []
  change: []
config: []
depends_on: [REQ-4.1, REQ-4.3, REQ-4.5]
nfr:
  resources:
    - "只读分页查询 + 单行更新；无新增线程/连接池"
  failure_modes:
    - "非 master：403"
    - "Key/别名不存在：404"
    - "参数非法：400"
    - "分页超限：收敛到 200"
  metrics:
    - "审计记录动作与目标；日志查询分页"
compat: additive
gate: auto
regression:
  - "REQ-1.3 Key 管理"
  - "REQ-2.1/2.4 MCP/Prompt 管理"
  - "REQ-4.1 租户隔离"
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 管理面统一 `/v1/admin` + master-only | 与数据面隔离；AuthFilter 统一 403 | 2026-11-16 |
| 不返回上游 api-key，任何情况下 | 凭证泄露风险最高；管理端只需要状态 | 2026-11-16 |
| Key 哈希作为管理标识 | SHA-256 不可逆，可关联日志/审计；明文只在生成时返回一次 | 2026-11-16 |
| 页面不做 | 需求卡明确页面可选；最小 API 优先，时间用于治理能力 | 2026-11-16 |
| 每页多取 1 条判 has_more | 避免额外 count 查询 | 2026-11-16 |

## 6. 执行摘要

- 2026-11-16：管理 API + 分页 + 脱敏 + 审计接入；`AdminIntegrationTest` 覆盖 Key 封禁/解封、
  模型 CRUD 不回显 api-key、项目授权解锁跨项目记忆、日志分页。
- 提交：f333e7d。

## 7. 复盘

- 管理端最容易复制业务鉴权导致越权，本期把所有 `/v1/admin` 收敛到入口过滤器。
- 审计与业务动作在同一 Service 调用内写入；后续可加事务消息保证强一致。
- 遗留：页面不做；Key 解封/封禁的操作人目前只有 master。

## 8. 多实例检查

- 管理操作直接写 DB，即时生效；无本地缓存（模型目录每请求读库）。
- 无单机锁；并发更新同一别名以最后写入为准。
