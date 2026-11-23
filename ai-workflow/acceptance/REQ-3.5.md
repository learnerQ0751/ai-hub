# REQ-3.5 验收：记忆服务（session/user）

- 状态：已验收（2026-11-11，理解默认通过）
- 验收人：主人
- 需求记录：`../records/REQ-3.5.md`
- 需求来源：外部需求卡 `12-项目7-第3期-RAG与记忆服务.md` REQ-3.5

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] `POST /v1/memory/save` 保存会话级/用户级记忆（`user_id`、`session_id`、`content`、`metadata`、`ttl_seconds`），返回 `memory_id` 与 `expires_at`；`session_id` 缺省为用户级记忆
- [x] `POST /v1/memory/recall` 按 `tenant_id + project_id + user_id`（+ 可选 `session_id`）召回；保存 → 召回命中，`content` 与 `metadata` 一致
- [x] 召回支持关键词与时间范围过滤；无命中返回空列表（不报错）
- [x] `POST /v1/memory/clear` 按用户/会话删除；clear 后 recall 不可见
- [x] clear 物理删除产生审计记录（`memory_audit_logs`：作用域、`deleted_count`、`memory_ids`、`operator_key`、`reason`）
- [x] 不同 user 记忆隔离；不同 project 默认隔离；跨项目共享默认关闭，仅可通过 `gateway.memory.cross-project-sharing-enabled` 开启，且仅用户级记忆可跨项目读取
- [x] 未开启跨项目共享时显式请求 `cross_project=true` 返回 403；会话级记忆请求跨项目返回 400
- [x] TTL 到期后 recall 自动不返回（查询时过滤，不依赖单机定时器）
- [x] 调用登记 `call_logs`：`call_type=MEMORY`、`target_name=save/recall/clear`、`cost=0`，且既有 MCP/PROMPT 分支回归通过
- [x] 隐私：`content` / `metadata` 不进日志明文，日志只记录 id、长度、计数、作用域
- [x] 向量检索接口预留：存在 `MemoryRecallPort` 端口，本期仅结构化（关键词/SQL）实现
- [x] 对外错误 `error.message` 为中文，`type` / `param` / `code` / HTTP 状态码保持英文/标准值（STANDARDS §3.6）
- [x] 非功能验收：clear 单事务（删除 + 审计同成败）；无新增连接池/线程池；TTL 惰性过滤，多实例安全（STANDARDS §2.7）
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；模块依赖方向符合 ArchUnit

**验收命令 / 示例**：

```bash
# 保存（会话级）
curl -X POST http://localhost:18081/v1/memory/save \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"tenant_id":"t1","project_id":"p1","user_id":"u1","session_id":"s1",
       "content":"用户对芒果过敏","metadata":{"source":"chat"},"ttl_seconds":3600}'

# 召回（关键词）
curl -X POST http://localhost:18081/v1/memory/recall \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"tenant_id":"t1","project_id":"p1","user_id":"u1","session_id":"s1","keyword":"芒果"}'

# 清理（用户级全量）
curl -X POST http://localhost:18081/v1/memory/clear \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"tenant_id":"t1","project_id":"p1","user_id":"u1","reason":"用户行使删除权"}'
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.5.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`MemoryServiceImpl`、`MemoryManager`、`GatewayGovernanceFilter`

## 3. 实现要点（按需阅读）

### 3.1 三条关键实现要点

1. 两级记忆与隔离：`session_id` 有值为会话级（绑定 project），为空为用户级；所有 SQL 强制 `tenant_id + user_id`，项目内再加 `project_id`。跨项目共享默认关闭，开启后仍需 `cross_project=true` 且只对用户级记忆生效，会话记忆永远不跨项目。
2. TTL 与删除审计：保存时算 `expires_at`，召回 SQL 过滤 `expires_at IS NULL OR expires_at > now()`，不依赖单机定时器；clear 在 Service 事务内「取 id → DELETE → INSERT memory_audit_logs」，审计记录作用域、实际条数、id 快照、operator key 与原因。
3. 治理复用与隐私：`GatewayGovernanceFilter` 识别 `/v1/memory/*` 为 `MEMORY` 调用，`GatewayGovernanceService` 只做 RPM 预占/结算并写 `call_logs`（cost=0，target=save/recall/clear）；日志只记录 id、长度、计数、作用域，不记录 `content` / `metadata`。

### 3.2 面试 1 分钟讲法（骨架）

```text
记忆服务解决跨会话/跨项目个性化，同时守住隐私边界。数据模型是 session/user 两级：
session_id 为空是用户画像，可配置同租户跨项目共享；有值则绑定项目，永不跨项目。
所有查询强制 tenant+user，跨项目要显式开关和参数。TTL 用查询时过滤，多实例无状态；
删除是物理删除，并在同一事务写审计表，满足用户删除权。对外提供 save/recall/clear 三个
REST 接口，复用网关统一治理只做 RPM 和调用日志，MCP 暴露留给下一需求。
```

## 4. 回归影响

- 需重跑的已有验收项：REQ-1.4（治理预占/结算）、REQ-1.5（call_logs 写入）、REQ-2.3 / REQ-2.5（MCP/PROMPT 的 call_type 分支）。
- 已随 `mvn -Pquality verify` 全量执行。

## 5. 验收记录

- 验收日期：2026-11-11
- 结论：理解验收默认通过（实现与 §1 全部对照项自检通过；待主人最终确认）
- 验证摘要：`mvn -Pquality verify` 全绿（262 个测试，记忆相关 29 个：13 服务单测 + 4 DAL + 8 + 3 集成 + 1 日志隐私）；覆盖保存/召回/清理、用户与项目隔离、跨项目开关、TTL、物理删除审计、MEMORY 调用日志与中文错误。
- 遗留问题：ER 图统一在第 3 期毕业验收时与 RAG 表合并输出 drawio/svg；MCP 暴露属 REQ-3.6。终端用户级鉴权不在本期（`user_id` 由调用方声明，越出作用域仅返回空结果），终端用户身份体系归 REQ-4.1 与业务方 Tier1。
