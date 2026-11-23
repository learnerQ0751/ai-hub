# REQ-2.2 验收：MCP 工具发现与聚合

- 状态：已验收（2026-11-03）
- 验收人：主人
- 需求记录：`../records/REQ-2.2.md`
- 需求来源：外部需求卡 REQ-2.2

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] 两个 Server 各 2 个工具 → `GET /v1/mcp/tools` 返回 4 条，对外名 `{server}__{tool}` 不冲突
- [x] 某个 Server 不可达/超时 → 其余工具正常返回，响应标记该 Server 降级
- [x] TTL 60s 内重复请求不重复访问上游；`POST /v1/mcp/tools/refresh` 后变更可见
- [x] 支持 `server_id` / `tenant_id` 过滤
- [x] 工具目录变更写日志（added/removed）
- [x] 注册/注销 MCP Server 后本实例缓存立即失效
- [x] `mvn -Pquality verify` 全绿

> 涉及对外错误/提示时，必须增加：`error.message` 为中文、`type`/`param`/`code`/HTTP 状态码保持不变（STANDARDS §3.6）。

**验收命令 / 示例**：

```bash
curl http://localhost:18080/v1/mcp/tools -H "Authorization: Bearer sk-local-master-key"
curl "http://localhost:18080/v1/mcp/tools?server_id=mock_http" -H "Authorization: Bearer sk-local-master-key"
curl -X POST http://localhost:18080/v1/mcp/tools/refresh -H "Authorization: Bearer sk-local-master-key"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-2.2.md` §1-3 人向说明（3 分钟）
3. `DESIGN.md` 架构图本次增量标注 + 核心文件 ≤3（5 分钟）

## 3. 实现要点（按需阅读）

### 3.1 三条关键实现要点

1. **并行聚合 + 局部降级**：有界线程池（4）并行调各 ACTIVE Server 的 `tools/list`，单 Server 异常只进 `degradedServers`，其余照常返回。
2. **快照缓存**：`McpToolCatalog` 用 volatile 不可变快照整体替换，TTL 60s；读无锁，注册/注销显式失效本实例。
3. **变更审计**：刷新时对比新旧快照，输出 `added/removed` 日志（后续 4.x 审计接入）。

### 3.2 面试 1 分钟讲法（骨架）

```text
Agent 需要统一工具目录：我聚合所有 ACTIVE MCP Server 的 tools/list，
对外命名 server__tool，保留原始名供调用转发。
两个关键点：一是并行聚合且单 Server 超时降级——LiteLLM 在这里是反例，它会整体 500；
二是本地快照缓存 TTL 60s，减少上游压力，注册/注销立即失效。
刷新时对比新旧目录并记录 added/removed，为后续审计留痕。
```

## 4. 回归影响

- 需重跑的已有验收项：REQ-2.1 注册/注销链路（新增缓存失效钩子）；全量门禁覆盖
- 真实链路回归：注册/注销、工具聚合、过滤、刷新全部正常

## 5. 验收记录

- 验收日期：2026-11-03
- 结论：理解验收默认通过（如需深挖看 §3）
- 遗留问题：无
