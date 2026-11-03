# REQ-2.6 验收：Agent 适配层与 MCP Server 暴露

- 状态：已验收（2026-11-03）
- 验收人：主人
- 需求记录：`../records/REQ-2.6.md`
- 需求来源：外部需求卡 REQ-2.6

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] MCP Client（LangChain4j `DefaultMcpClient`）能发现 ai-hub 暴露的工具并完成一次 chat 调用
- [x] REST 与 MCP 两条路径结果一致（同一模型/参数）
- [x] MCP 调用在日志中可追踪（`call_type=MCP`）
- [x] `mvn -Pquality verify` 全绿

> 涉及对外错误/提示时，必须增加：`error.message` 为中文、`type`/`param`/`code`/HTTP 状态码保持不变（STANDARDS §3.6）。

**验收命令 / 示例**：

```bash
curl -X POST http://localhost:18080/mcp \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-2.6.md` §1-3 人向说明（3 分钟）
3. `DESIGN.md` 架构图本次增量标注 + 核心文件 ≤3（5 分钟）

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **双协议单实现**：REST 与 MCP 共用同一批 Service；`chat_completion` 内部调 `ChatCompletionService`，`prompts_get` 调 `PromptChatService.render`，行为天然一致。
2. **原生 JSON-RPC**：单响应 application/json（notification 返回 202），LangChain4j `StreamableHttpMcpTransport` 实测兼容。
3. **治理接入**：`/mcp` 纳入 GatewayGovernanceFilter，tools/call 的 target 记工具名；MCP 层按 RPM + cost=0 口径（与 2.3 一致）。

### 3.2 面试 1 分钟讲法（骨架）

```text
ai-hub 不只对外提供 REST，还把自己暴露成 MCP Server：POST /mcp 支持
initialize/tools/list/tools/call，工具是 chat_completion、list_models、prompts_get。
关键点：所有工具复用已有 Service，所以 REST 和 MCP 双路径结果天然一致；
测试直接用 LangChain4j 官方 MCP 客户端跑通发现和调用；治理统一走同一个 Filter。
```

### 3.3 自测题与参考答案

1. 为什么不用 MCP Server SDK？
2. MCP 响应为什么用 JSON 而不是 SSE？
3. MCP 路径的治理为什么只做 RPM、cost=0？
4. 如何保证 REST/MCP 双路径一致？
5. LangChain4j 客户端测试解决了什么问题？

### 参考答案

1. 只需 3 个工具，原生 JSON-RPC 实现简单直接；引 SDK 会带来新的 Web 栈/依赖面。
2. Streamable HTTP 规范允许 application/json；实现简单，LangChain4j 客户端实测可用。
3. MCP 层不解析嵌套 arguments 的 model/usage，深度成本计量留 4.x；RPM 防滥用，日志保留可追踪性。
4. 双路径共用同一 Service/Core，不存在第二套业务逻辑。
5. 验证真实 MCP 客户端协议兼容性（initialize/会话/工具发现/调用），而非仅自测自造请求。

### 3.4 亲手改的口子（可选）

- 任务：把 MCP 治理升级为识别 chat_completion 的 model 并走完整 chat 计量。
- 验证方法：MCP chat 调用日志出现 usage 与 cost。

## 4. 回归影响

- 需重跑的已有验收项：AuthFilter 全路径鉴权、GatewayGovernanceFilter 路径分类
- 真实链路回归：REST chat、MCP chat、prompts_get 均正常

## 5. 验收记录

- 验收日期：2026-11-03
- 结论：理解验收默认通过（如需深挖看 §3）
- 遗留问题：MCP chat_completion 的模型成本未计入（已知限制，4.x 统一）
