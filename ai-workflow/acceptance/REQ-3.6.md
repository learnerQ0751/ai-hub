# REQ-3.6 验收：RAG/记忆 MCP Server 暴露

- 状态：待验收（2026-11-11，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-3.6.md`
- 需求来源：外部需求卡 `12-项目7-第3期-RAG与记忆服务.md` REQ-3.6

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] `POST /mcp` 的 `tools/list` 暴露 6 个新工具：`ingest_document`、`search_knowledge`、`rag_answer`、
      `save_memory`、`recall_memory`、`clear_memory`；既有 `chat_completion` / `list_models` / `prompts_get`
      与聚合工具仍可见
- [x] 工具 `inputSchema` 与 REST 契约字段一致（snake_case）：RAG 检索含 `query`/`project_id`/`top_k`/
      `min_score`/`filters`/`rerank`；RAG 生成另含 `model`/`allow_free_answer`；文档接入含 `project_id`/
      `file_name`/`content_type`/`content` 或 `content_base64`；记忆 save/recall/clear 的字段与
      `/v1/memory/*` 一致（`tenant_id`/`project_id`/`user_id`/`session_id` 等）
- [x] MCP Client（LangChain4j）调用 `search_knowledge` 返回正确切片：已知语料命中期望 `doc_id`/`source`/
      `content`，`project_id` 隔离生效，无命中返回空列表且 `no_hit=true`
- [x] MCP Client 调用 `ingest_document`（文本或 base64 内容）可接入文档，返回文档视图；
      `rag_answer` 返回 `answer` + `citations[]`，引用与检索命中一致
- [x] MCP Client 调用 `save_memory` → `recall_memory` 命中同一内容；`clear_memory` 后召回不可见，
      并产生物理删除审计
- [x] 跨项目记忆授权符合 REQ-3.5 规则：默认项目隔离（B 项目读取 A 项目记忆为空）；
      服务端开关关闭时 `cross_project=true` 被拒（JSON-RPC error，中文提示含未开启语义）；
      开关开启后仅用户级记忆可跨项目读取；会话级记忆跨项目请求被拒
- [x] 未授权调用被拒：无 Bearer 的 `/mcp` 请求返回 401（AuthFilter）；跨租户读取返回空结果不泄露
- [x] 调用日志与 Trace 打通：`call_logs` 记录 `call_type=MCP`、`target_name=工具名`（如 `search_knowledge`）、
      `request_id` 与入口 `X-Request-Id` 一致；工具入口日志带同一 `request_id`，不打印记忆正文/文档全文
- [x] 错误语义：工具参数非法与业务拒绝以 JSON-RPC error 返回，`message` 为中文；REST 既有
      `type`/`param`/`code`/HTTP 状态码不因本次改动变化（STANDARDS §3.6）
- [x] 非功能验收：无新增线程池/连接池；MCP 端点无本地业务状态；复用既有治理（不在工具内复制限流/日志）
      （STANDARDS §2.7）
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；模块依赖方向符合 ArchUnit

**验收命令 / 示例**：

```bash
# 1) 工具发现（LangChain4j 客户端或裸 JSON-RPC）
curl -X POST http://localhost:18081/mcp \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# 2) 检索（MCP）
curl -X POST http://localhost:18081/mcp \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search_knowledge",
       "arguments":{"query":"年假有多少天","project_id":"p-rag-demo"}}}'

# 3) 写记忆（项目 A）→ 跨项目读取（项目 B，需开启 cross-project-sharing-enabled）
curl -X POST http://localhost:18081/mcp \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"save_memory",
       "arguments":{"tenant_id":"t1","project_id":"p-a","user_id":"u1","content":"用户对芒果过敏"}}}'

# 4) 未授权：未开启跨项目共享时显式请求 → JSON-RPC error（中文）
curl -X POST http://localhost:18081/mcp \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"recall_memory",
       "arguments":{"tenant_id":"t1","project_id":"p-b","user_id":"u1","cross_project":true}}}'

# 5) 未授权：无 Bearer → 401
curl -i -X POST http://localhost:18081/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/list"}'

# 6) 离线回归（H2 + 确定性 embedding + 内存向量存储）
mvn -pl ai-hub-tests -am test -Dtest=RagMemoryMcpIntegrationTest
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.6.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`McpRagMemoryTools`、`McpServerEndpoint`、`GatewayGovernanceFilter`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. 双协议、一份业务：`McpRagMemoryTools` 把 MCP 参数转成与 REST 相同的 DTO，直接调用
   `RagDocumentService` / `RagSearchService` / `RagAnswerService` / `MemoryService`；MCP 层没有
   第二套校验、检索或权限逻辑。
2. 授权不搬家：记忆的租户/项目/用户作用域与跨项目开关完全由 REQ-3.5 的 Service 判定。
   MCP 只透传参数与 API Key 哈希（clear 审计用），因此 REST 与 MCP 的越权结论必然一致。
3. 治理与链路：`/mcp` 由统一 Filter 治理，`tools/call` 的目标名从 JSON-RPC 参数解析，
   `call_logs` 记 `MCP/工具名/cost=0`；工具入口日志携带同一 `request_id`，且不打印正文。

### 3.2 面试 1 分钟讲法（骨架）

```text
RAG 和记忆先做成 REST 能力，再在同一个 /mcp 上暴露成标准工具。关键不是再写一套 MCP 业务，
而是把 MCP 当适配层：参数 schema 与 REST 对齐，工具直接调用同一批 Service。租户和权限
天然不丢失，因为授权仍在 Service 里。治理也只做一次：MCP 入口统一做 RPM 和调用日志，
call_logs 记录工具名和 request_id，能跟 4.x Trace 对上。
```

### 3.3 自测题与参考答案

1. 为什么 MCP 工具不直接复用 `/v1/rag/search` 的 HTTP 接口，而是调用 Service？
2. 工具 schema 与 REST 契约不一致会有什么问题？
3. MCP 调用会不会绕过限流？为什么？
4. 为什么 `ingest_document` 需要 `content_base64`？
5. 跨项目读取记忆时，MCP 层做了哪些授权？

### 参考答案

1. HTTP 自调用会多一次网络跳转，并再次经过 Filter 造成双重限流/计量；直接调 Service 与
   REQ-2.5/3.4 的做法一致，业务逻辑只有一份。
2. Agent 按 schema 生成的参数会与后端 DTO 不匹配，出现“能发现工具但调用必失败”；
   字段名、必填项、类型必须同源，本实现直接复用同一 DTO。
3. 不会。`/mcp` 已在 `GatewayGovernanceFilter` 的治理范围，`tools/call` 的工具名会作为
   target 记录；预占/结算/日志都在入口完成。
4. JSON-RPC 参数是 JSON，不能承载 multipart 二进制；PDF 等文件需要 Base64 封装，
   服务端解码后复用同一文档接入流程。
5. MCP 层只透传 `tenant_id/project_id/user_id/session_id/cross_project`；是否允许跨项目由
   Service 按配置开关、记忆级别（仅用户级）和租户一致性判定，未开启时直接拒绝。

### 3.4 亲手改的口子（可选）

- 任务：把 `gateway.memory.cross-project-sharing-enabled` 改为 `true`，重启后用 MCP
  `recall_memory` 在项目 B 以 `cross_project=true` 读取项目 A 的用户级记忆；再用 `session_id`
  重复一次观察拒绝。
- 验证方法：`tools/call` 的返回内容与 JSON-RPC error message 对照本文 §1。

## 4. 回归影响

- 需重跑的已有验收项：REQ-2.6/2.7（平台工具与聚合工具）、REQ-3.1~3.4（RAG REST）、
  REQ-3.5（记忆 REST 与跨项目开关）、REQ-1.4/1.5（MCP 治理与 `call_logs`）。
- 已随 `mvn -Pquality verify` 全量执行（379 个测试）。

## 5. 验收记录

- 验收日期：2026-11-11（实现与自检完成；待主人最终确认）
- 结论：理解验收默认通过（如需深挖看 §3）
- 验证摘要：`mvn -Pquality verify` 全绿（379 个测试，0 失败）；新增 10 个 MCP 集成测试；
  真实链路（pgvector + all-minilm + DeepSeek）演示工具发现、检索命中、引用、无召回兜底、
  跨项目授权与拒绝、跨租户隔离、模型切换未重建与重建恢复。
- 遗留问题：JSON-RPC 业务错误在 `call_logs` 中仍记 HTTP 200（协议层语义），工具级错误率
  统计留给第 4 期 Trace；终端用户级鉴权仍归 REQ-4.1 与业务方 Tier1。
