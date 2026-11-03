# REQ-2.6：Agent 适配层与 MCP Server 暴露

- 日期：2026-11-03
- 状态：已完成
- 关联规格：无（需求卡 `11-项目7-第2期-MCP与Prompt中心.md` §REQ-2.6 + `18-项目7-第2期需求拆解.md` 2.6.1-2.6.3）
- 依赖需求：REQ-2.1、REQ-2.2、REQ-2.3、REQ-2.5
- 需求来源：外部需求卡 REQ-2.6
- 提交：3dd2905

## 1. 是什么

项目 7 的形态是「AI 中枢」——业务通过 REST 或 MCP 两种方式消费 ai-hub 能力。
本需求把 ai-hub 自身以 MCP Server 形式暴露（原生 JSON-RPC HTTP，`POST /mcp`），
提供 `chat_completion` / `list_models` / `prompts_get` 三个工具，并用 LangChain4j
客户端验证「发现 → 调用」与 REST 双路径一致性。

## 2. 有什么用

- 业务价值：Agent 生态入口——Claude/Cursor 等 MCP 客户端可直接消费网关能力
- 对应参照行为：
  - LiteLLM 以 MCP Gateway 聚合上游 Server；本需求是反向：ai-hub 自身作为 MCP Server 暴露
  - 参照 07 笔记 §9.2「REST 为主路径，MCP 为 Agent 适配层」
- 面试叙事点：一个服务同时提供 REST 与 MCP 双协议；LLM 能力工具化；双路径一致性验证

## 3. 怎么实现的

- 分层落点：`start/mcp`（McpServerEndpoint：JSON-RPC 方法路由 + 工具 schema/调用）、`start/filter`（GatewayGovernanceFilter：/mcp 纳入统一治理）；复用 `ChatCompletionService` / `ModelService` / `PromptChatService`
- 核心类（≤3）：`McpServerEndpoint`（initialize/tools/list/tools/call）、`GatewayGovernanceFilter`（MCP 目标解析 + 治理）、LangChain4j `DefaultMcpClient` + `StreamableHttpMcpTransport`（示例客户端）
- 请求链路（≤8 行）：

```text
POST /mcp（JSON-RPC 2.0）
→ AuthFilter（任意 Key）→ GatewayGovernanceFilter（识别 /mcp，target=method 或 tools/call 的工具名，MCP 治理口径）
→ McpServerEndpoint
  initialize → 协议版本 2025-06-18 + capabilities.tools
  tools/list → chat_completion / list_models / prompts_get schema
  tools/call → chat_completion（内部 ChatCompletionService）/ list_models（ModelService）
              / prompts_get（PromptChatService.render）
  响应 JSON 单响应（notification → 202）
```

## 4. 影响面

```yaml
capability:
  type: business（Agent 适配层）
  entry_point: "Start（/mcp endpoint）+ GatewayGovernanceFilter（MCP 治理）"
  interface_agnostic: false
modules:
  add: [start/mcp]
  change: [start/filter/GatewayGovernanceFilter（/mcp 治理分类与目标解析）]
interfaces:
  add: ["POST /mcp（JSON-RPC：initialize/tools/list/tools/call）"]
  change: []
  remove: []
tables:
  add: []
  change: []
config: []
depends_on: [REQ-2.1, REQ-2.2, REQ-2.3, REQ-2.5]
compat: additive
gate: auto
regression: ["AuthFilter 全路径鉴权", "GatewayGovernanceFilter 路径分类（新增 /mcp）"]
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 原生 JSON-RPC HTTP（不引入 MCP Server SDK） | 只需 3 个工具；避免新框架/双 Web 栈 | 2026-11-03 |
| 单响应 JSON（notification 202） | MCP Streamable HTTP 允许 application/json；LangChain4j 客户端实测可用 | 2026-11-03 |
| chat_completion 复用内部 ChatCompletionService | 与 REST 完全一致的路由/容错/计量口径 | 2026-11-03 |
| /mcp 按 MCP 治理口径（RPM + 日志 cost=0） | 与 2.3 一致；MCP 层不解析嵌套 arguments 的 model。深度成本计量留 4.x | 2026-11-03 |
| 测试用 LangChain4j DefaultMcpClient + StreamableHttpMcpTransport | 需求卡指定；验证真实 MCP 客户端兼容性 | 2026-11-03 |

## 6. 执行摘要

### 2026-11-03

- 进展：实现完成（/mcp endpoint + 3 工具 + 治理接入），门禁全绿；集成测试 4/4（LangChain4j 发现/调用、REST/MCP 一致、prompts_get、list_models）；真实链路验证通过（initialize/tools-list/chat_completion 真实 DeepSeek/prompts_get）。
- 踩坑：治理目标解析从 request attribute 读 body 时机过早（target 变成 "mcp"）→ wrapper 创建提前并直接传 body。
- 下一步：二期毕业验收。

## 7. 复盘

- 做对了什么：工具实现全部复用既有 service（零重复业务）；LangChain4j 客户端实测通过，不是纸上兼容。
- 踩了什么坑：Filter 内 body 可用的时机（先建 wrapper 再解析）。
- 下次改进：Filter 扩展先画清"数据可用性时序"。

## 8. 多实例检查

- [x] 是否有可变成员变量？是否线程安全、跨实例一致？——无状态
- [x] 计数器/限流/预算/冷却是否在 Redis/数据库？——RPM 在 Redis；日志在 DB
- [x] 锁是否分布式？是否有超时、防死锁、幂等？——无新增锁
- [x] 定时任务是否只执行一次？——无
- [x] 本地缓存是否可容忍过期/不一致？——同 2.2 工具目录
