# REQ-4.1 验收：多租户贯穿与隔离

- 状态：待验收（2026-11-16，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-4.1.md`
- 需求来源：第 4 期需求卡 + 三期验收遗留（虚拟 Key 绑租户、表结构拆分、记忆四维隔离）

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] 虚拟 Key 绑定 `(tenant_id, project_id)`，`/key/generate` 支持可选绑定并在响应回显；
      未绑定 Key 访问租户资源返回 403（禁止默认全局可见）
- [x] `tenant_id/project_id` 只来自 Key：请求参数不一致返回 403 `tenant_access_denied`，
      并记录含 keyHash、请求值与绑定值的 WARN 日志
- [x] A 租户 Key 无法读取 B 租户的 Prompt（render 按租户解析）、RAG 文档（上传/列表/删除按租户）、
      记忆（save/recall/clear 按租户）；MCP 工具调用按 Server 租户校验
- [x] 记忆跨项目默认隔离；`project_authorizations` 显式授权后，同租户跨项目记忆可读写
- [x] RAG 检索向量通道按 `tenant_id + project_id + embedding_model` 先过滤再排序；
      切片回填再校验租户/项目，双保险
- [x] 调用日志记录 `tenant_id/project_id/trace_id`；日志查询的租户过滤在 REQ-4.4 管理 API 暴露
- [x] 别名/部署实例/别名↔Key/别名↔部署四张表落地；别名配置由 YAML 迁到 DB，空库时种子灌入
- [x] 上游凭证入库为 AES-256-GCM 密文（未配置 `gateway.secret-key` 时弱编码 + WARN）
- [x] 越权统一 403（虚拟 Key 访问 `/v1/admin*` 为 403 `authorization_error`）
- [x] `mvn test` 全绿（128 个测试类中的集成用例含 `TenantIsolationIntegrationTest` 6 例）；
      ArchUnit 依赖方向通过；持久层仍为 MyBatis

**验收命令 / 示例**：

```bash
# 生成两个租户的 Key（master）
curl -X POST http://localhost:18080/key/generate -H "Authorization: Bearer $MASTER" \
  -H 'Content-Type: application/json' \
  -d '{"models":[],"tenant_id":"tenant-a","project_id":"project-a"}'

# A 租户保存/召回记忆
curl -X POST http://localhost:18080/v1/memory/save -H "Authorization: Bearer $KEY_A" \
  -H 'Content-Type: application/json' -d '{"user_id":"u1","session_id":"s1","content":"a"}'
curl -X POST http://localhost:18080/v1/memory/recall -H "Authorization: Bearer $KEY_A" \
  -H 'Content-Type: application/json' -d '{"user_id":"u1","session_id":"s1"}'

# B 租户伪造 tenant_id → 403
curl -X POST http://localhost:18080/v1/memory/recall -H "Authorization: Bearer $KEY_B" \
  -H 'Content-Type: application/json' \
  -d '{"tenant_id":"tenant-a","project_id":"project-a","user_id":"u1"}'
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-4.1.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`common/tenant/TenantScopeGuard`、`start/filter/AuthFilter`、
   `db/migration/V10__tenancy_and_model_catalog.sql`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **Key 是租户唯一来源**：`AuthFilter` 把 `KeyAuthDTO(tenant_id, project_id, authorized_projects)`
   转成 `TenantIdentity` 写入 ThreadLocal；Service 的 `TenantScopeGuard.resolve` 只校验请求声明，
   不一致 403，绝不覆盖绑定。
2. **服务端派生过滤**：RAG/记忆/Prompt/MCP/日志的 SQL 都带租户条件；向量检索先过滤再排序；
   跨项目只在授权表命中时放行。
3. **清理与多实例**：ThreadLocal 在请求 `finally` 清理；目录/Key/授权/日志全部持久化，
   目录每请求读库，多实例一致。

### 3.2 面试 1 分钟讲法（骨架）

```text
虚拟 Key 在签发时绑定租户和项目，鉴权后把租户身份放进请求上下文。业务层只信这个上下文：
请求里写了 tenant/project 就跟绑定值比对，不一致直接 403 并打日志；资源 SQL 一律带租户条件，
向量检索先按租户+项目过滤再算相似度。跨项目共享不靠开关，而是项目级授权表。模型别名和部署
实例也迁到库里，管理端改完多实例立即可见。
```

### 3.3 自测题与参考答案

1. 为什么不能信请求体里的 `tenant_id/project_id`？
2. 虚拟 Key 和 master key 的租户语义有什么区别？
3. 跨项目记忆为什么用授权表而不是全局开关？
4. ThreadLocal 上下文为什么必须在 finally 清理？
5. 向量检索的租户过滤应该放在排序前还是排序后？

### 参考答案

1. 请求体是调用方可控输入；只信 Key 绑定才能保证“知道别人 tenant 也读不到”。
2. master 是管理身份，可显式指定租户；虚拟 Key 只能在自己的绑定/授权范围内活动。
3. 开关只能表达“允许跨”，无法表达“谁能跨到谁”；授权表可审计、可撤销、按项目粒度。
4. Web 容器线程复用；不清理会让下一个请求读到上一个租户的上下文，造成串租户。
5. 必须先过滤再排序：先排序再过滤会改变 topK 语义，也可能把别的租户数据带进计算路径。

## 4. 亲手改口子（可选）

- 把 `TenantScopeGuard.DEFAULT_TENANT` 改成别的值，观察未绑定 Key/默认租户数据的表现。
- 在 `project_authorizations` 插入一条反向授权，验证 B 项目 Key 可读 A 项目记忆。
- 把 `gateway.secret-key` 置空后重启，观察上游凭证弱编码 WARN（生产禁止）。
