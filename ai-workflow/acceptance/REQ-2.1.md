# REQ-2.1 验收：MCP Server 注册与发现

- 状态：已验收（2026-11-03）
- 验收人：主人
- 需求记录：`../records/REQ-2.1.md`
- 需求来源：外部需求卡 REQ-2.1（`11-项目7-第2期-MCP与Prompt中心.md`）

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] 注册 HTTP mock Server 与 stdio mock Server → `GET /v1/mcp/servers` 返回 2 条，状态均为 `ACTIVE`
- [x] 不可达 HTTP Server 注册成功但状态 `DOWN`，`lastError` 可查；手动重探接口返回最新状态
- [x] 注销后列表消失；注销不存在的 serverId → 404
- [x] 重复注册同名 → 409；server name 含 `__` → 400
- [x] endpoint 含 userinfo/敏感 query 时，响应中已掩码
- [x] 管理接口仅 master key：虚拟 Key 调用 → 401
- [x] 应用启动后自动加载 `ACTIVE` Server 并重新探活（日志可证）
- [x] `mvn -Pquality verify` 全绿

> 涉及对外错误/提示时，必须增加：`error.message` 为中文、`type`/`param`/`code`/HTTP 状态码保持不变（STANDARDS §3.6）。

**验收命令 / 示例**：

```bash
# 注册（master）
curl -X POST http://localhost:18080/v1/mcp/servers \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"name":"mock_http","transport":"http","endpoint":"http://127.0.0.1:8001/mcp"}'

# 列表 / 重探 / 注销
curl http://localhost:18080/v1/mcp/servers -H "Authorization: Bearer sk-local-master-key"
curl -X POST http://localhost:18080/v1/mcp/servers/{serverId}/reprobe -H "Authorization: Bearer sk-local-master-key"
curl -X DELETE http://localhost:18080/v1/mcp/servers/{serverId} -H "Authorization: Bearer sk-local-master-key"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-2.1.md` §1-3 人向说明（3 分钟）
3. `DESIGN.md` 架构图本次增量标注 + 核心文件 ≤3（5 分钟）

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **注册即探活，失败不阻断**：HTTP 发 JSON-RPC `initialize`（3s 超时，响应兼容 application/json 与 SSE `data:` 行）；stdio 启动子进程写**单行** JSON-RPC、读一行响应（3s 超时强杀）。探活失败注册仍成功，状态 `DOWN` + `metadata.lastError`，可手动 `reprobe`。
2. **分层依赖收口**：探活入参不用 manager 的 BO（integration 不能依赖 manager），收敛为 common 的 `McpProbeTarget`；启动加载器放 service 层（ArchUnit 禁止 start→manager）。
3. **防护**：server name 禁止 `__`（工具名分隔符，T1 校准自 LiteLLM 的 `-` 限制）；endpoint 输出掩码 userinfo 与敏感 query；管理面仅 master key；stdio 子进程注册表 + shutdown hook 防泄漏。

### 3.2 面试 1 分钟讲法（骨架）

```text
MCP 集群需要注册表：上游 Agent 和网关都要知道有哪些 MCP Server、地址、是否可用。
我实现的是管理面：注册 / 列表 / 注销 / 重探。
关键设计：注册时同步做 MCP initialize 探活（HTTP 3 秒超时、stdio 子进程逐行协议），
探活失败也允许注册，但状态是 DOWN 并记录错误，支持手动重探。
和 LiteLLM 的差异：LiteLLM 注册不探活、且一个不可达 Server 会让整个 MCP 列表 500；
我把故障隔离到单条记录，列表永远可用。
```

### 3.3 自测题与参考答案

1. 为什么注册要同步探活？失败为什么还允许注册？
2. stdio 探活为什么要用单行 JSON？
3. 为什么探活入参单独定义 McpProbeTarget，而不是直接用 McpServerBO？
4. endpoint 掩码在哪一层做？为什么不入库前掩码？
5. 多实例部署时启动加载并行探活会有什么问题？

### 参考答案

1. 同步探活让注册响应直接给出真实状态，用户无需再查；失败允许注册是为了"登记意图"不丢失（Server 可能稍后恢复），错误可通过 `reprobe` 修正。
2. stdio 传输以换行分帧；多行 JSON 会被对端按行截断解析失败（真实链路踩过：Python mock 严格解析 → 超时）。
3. ArchUnit 分层约束：integration 不得依赖 manager；探活只需要连接信息，用 common 的领域对象最合适。
4. 在 service 的 DTO 转换时做；DB 保存原始值（探活需要真实 endpoint），只在对外视图脱敏。
5. 每个实例都会探活一次并写同一 DB，只是重复开销，无一致性风险（状态是"最近一次探活结果"的覆盖写）。

### 3.4 亲手改的口子（可选）

- 任务：把探活超时从 3s 改为可配置（如 `gateway.mcp.probe-timeout`）。
- 验证方法：注册一个 sleep 5s 的 mock Server，默认 3s 超时 DOWN；调大配置后重探变 ACTIVE。

## 4. 回归影响

- 需重跑的已有验收项：`/key/*` 管理面（AuthFilter 扩展）；聊天链路不受影响（全量门禁自动覆盖）
- 真实链路回归：master key 管理接口、虚拟 Key 聊天调用均正常（见全量门禁与真实链路记录）

## 5. 验收记录

- 验收日期：2026-11-03
- 结论：理解验收默认通过（如需深挖看 §3）
- 遗留问题：无（stdio 多行 JSON 坑已修复并有严格 mock 回归覆盖）
