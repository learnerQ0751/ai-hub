# ai-hub 接口路径表（前端对接用）

> 版本：REQ-5.1~5.4，**契约冻结 2026-11-17**。数据面与控制面拆为两个应用，管理台只连 console。
> 契约与 4.4 保持一致：管理端点路径不迁移，隔离手段是「console 只认 admin 凭证」。

## 0. 契约冻结声明（2026-11-17）

第 5 期（REQ-5.1~5.4）落地后，本表所列对外契约一次性冻结。
冻结对象：路径、请求/响应字段、鉴权方式、错误码、分页与筛选参数。

### 0.1 冻结清单

| # | 冻结项 | 状态 |
|---|---|---|
| 1 | 两应用划分、默认端口与健康检查（§1） | 已实现，已冻结 |
| 2 | 反向代理精确路径切分（§2） | 已实现，已冻结 |
| 3 | gateway 鉴权：虚拟 Key；master key → 403（§1、§6） | 已实现，已冻结 |
| 4 | console 鉴权：master key；虚拟 Key → 403（§1、§6） | 已实现，已冻结 |
| 5 | 数据面端点与别名路径（§3） | 已实现，已冻结 |
| 6 | `/v1/rag/search` 命中字段 `match_type`、`vector_score`、`keyword_score`、`rrf_score`；`score` 仍为向量相似度（§3.1） | 已实现，已冻结 |
| 7 | `/v1/rag/answer` 请求/响应字段不变；上下文顺序与邻居扩展行为（§3.2） | 已实现，已冻结 |
| 8 | 控制面端点（§4）与可选 `Idempotency-Key` 语义（§5） | 已实现，已冻结 |
| 9 | 统一错误体与错误码（§6） | 已实现，已冻结 |
| 10 | 现有分页与筛选参数（§3、§4.7 所列） | 已实现，已冻结 |
| 11 | §7.2 字段级修订清单 | 方向已冻结，未实现 |
| 12 | §7.1 端点缺口 | 仅登记，不冻结 |

### 0.2 冻结后的变更纪律

1. 任何契约变更必须先新立需求（REQ-x.y），走影响面门禁与验收标准冻结；不得在实现中单方面修改本表。
2. additive 变更（新增字段/端点）：须在需求记录与本文同步登记，并保持旧客户端行为不变。
3. 破坏性变更（改路径、改字段语义或类型、改鉴权、改错误码、改分页/筛选口径）：必须给出迁移方案与前端对齐窗口，并在本文升版本号。
4. 标注「未实现」的条目在实现并验证前不构成已生效契约，客户端不得依赖其返回。

## 1. 应用与端口

| 应用 | 默认端口 | 职责 | 鉴权 |
|---|---|---|---|
| `ai-hub-gateway`（数据面） | 18080 | 聊天、嵌入、模型列表、MCP 调用、Prompt 运行时、RAG 检索/生成、记忆 | 虚拟 Key（`Authorization: Bearer sk-...`）；master key 一律 403 |
| `ai-hub-console`（控制面） | 18081 | Key 管理、模型注册、MCP 注册、Prompt 模板/发布、RAG 文档接入、用量/审计/Trace 查询 | admin 凭证（`Authorization: Bearer $GATEWAY_MASTER_KEY`）；虚拟 Key 一律 403 |

健康检查：两应用均提供 `GET /actuator/health`（免鉴权，容器探针使用）。

## 2. 网关路由切分（反向代理必须按精确路径转发）

- `/v1/rag/search`、`/v1/rag/answer` → **gateway**
- `/v1/rag/documents*` → **console**
- 其余 `/v1/chat/**`、`/v1/embeddings`、`/v1/models`、`/v1/mcp/tools*`、`/mcp`、`/v1/prompts/{name}/render|chat`、`/v1/memory/*` → **gateway**
- `/key/*`、`/v1/prompts*`（除 render/chat）、`/v1/mcp/servers*`、`/v1/mcp/runtime/*`、`/v1/traces/*`、`/v1/usage*`、`/v1/audit-logs`、`/v1/admin/*` → **console**

## 3. 数据面（gateway :18080，虚拟 Key）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/chat/completions`、`/chat/completions` | OpenAI 兼容聊天，支持 SSE |
| POST | `/v1/embeddings`、`/embeddings` | 文本向量 |
| GET | `/v1/models`、`/models` | 可用模型别名列表 |
| POST | `/v1/mcp/tools/{toolName}:call` | 调用聚合后的 MCP 工具 |
| GET | `/v1/mcp/tools` | 工具目录（可选 `server_id`、`tenant_id`） |
| POST | `/v1/mcp/tools/refresh` | 刷新工具目录缓存 |
| POST | `/mcp` | 原生 MCP JSON-RPC 2.0 端点 |
| POST | `/v1/prompts/{name}/render` | 运行时渲染 Prompt |
| POST | `/v1/prompts/{name}/chat` | 渲染后直接对话 |
| POST | `/v1/rag/search` | 知识库检索 |
| POST | `/v1/rag/answer` | 检索 + 生成回答 |
| POST | `/v1/memory/save`、`/recall`、`/clear` | 记忆写入/召回/删除 |

### 3.1 `/v1/rag/search` 命中字段（REQ-5.3，additive）

```json
{
  "chunk_id": "...", "doc_id": "...", "source": "a.md", "chunk_index": 0,
  "content": "...",
  "score": 0.83,             // 向量相似度；仅字面命中为 0
  "rerank_score": null,      // 仅关闭混合检索且启用规则重排时非空
  "match_type": "both",      // vector / keyword / both
  "vector_score": 0.83,      // 向量路原始分；未命中为 null
  "keyword_score": 0.5,      // 字面路覆盖率；未命中为 null
  "rrf_score": 0.032        // 融合排序分；关闭混合检索为 null
}
```

路径与鉴权不变；上述字段名与语义已冻结（2026-11-17），新增字段须走新需求登记。

### 3.2 `/v1/rag/answer` 请求与响应（REQ-5.4，已冻结）

- 请求与响应字段不变；路径与鉴权不变。
- 内部变化：上下文按文档分组、组内按 `chunk_index` 升序拼装；同文档多命中在 prompt 内连续。
- 默认邻居扩展 `neighbor-window=1`；设为 0 关闭，仅还原顺序。
- 扩展邻居在 prompt 中标记「（上下文）」，不进入 `citations`；`citations` 只含原命中 `chunk_id`。
- 字符预算（`answer.max-context-chars`）优先保住引用块，至少保留第一条引用。
- 已冻结（2026-11-17）：调整默认窗口或引用组成属于契约变更，须走新需求。

## 4. 控制面（console :18081，admin 凭证）

### 4.1 Key

| 方法 | 路径 |
|---|---|
| POST | `/key/generate` |
| GET | `/key/info?key=` |
| POST | `/key/block`、`/key/unblock` |
| GET | `/v1/admin/keys?tenant_id=` |
| POST | `/v1/admin/keys/{keyHash}/block`、`/unblock` |

### 4.2 模型与日志

| 方法 | 路径 |
|---|---|
| GET/POST | `/v1/admin/models` |
| GET/PUT/DELETE | `/v1/admin/models/{alias}` |
| POST | `/v1/admin/models/{alias}/deployments` |
| GET | `/v1/admin/logs` |
| POST | `/v1/admin/project-authorizations` |

### 4.3 MCP 注册表

| 方法 | 路径 |
|---|---|
| GET/POST | `/v1/mcp/servers` |
| DELETE | `/v1/mcp/servers/{serverId}` |
| POST | `/v1/mcp/servers/{serverId}/reprobe` |
| GET | `/v1/mcp/runtime/stats` |

### 4.4 Prompt 模板

| 方法 | 路径 |
|---|---|
| GET/POST | `/v1/prompts` |
| GET | `/v1/prompts/{name}`、`/v1/prompts/{name}/versions` |
| POST | `/v1/prompts/{name}/versions`、`/v1/prompts/{name}/publish` |

### 4.5 RAG 文档

| 方法 | 路径 |
|---|---|
| GET/POST | `/v1/rag/documents`（POST multipart：`file` + `project_id`） |
| DELETE | `/v1/rag/documents/{docId}` |
| POST | `/v1/rag/documents/{docId}/reindex` |

### 4.6 查询

| 方法 | 路径 |
|---|---|
| GET | `/v1/traces/{traceId}` |
| GET | `/v1/usage`、`/v1/usage/export` |
| GET | `/v1/audit-logs` |

### 4.7 分页与筛选参数（已冻结，2026-11-17）

| 端点 | 参数 |
|---|---|
| `GET /v1/admin/keys` | 可选 `tenant_id`、`project_id`；`limit`（默认 50）、`offset`（默认 0） |
| `GET /v1/admin/logs` | 可选 `tenant_id`、`project_id`、`model`、`key_id`、`status`、`from`、`to`；`limit`（默认 50）、`offset`（默认 0） |
| `GET /v1/usage`、`/v1/usage/export` | 可选 `from`、`to`、`tenant_id`、`project_id`、`model`、`key_id` |
| `GET /v1/audit-logs` | 可选 `tenant_id`、`action`、`target`、`from`、`to`；`limit`（默认 50）、`offset`（默认 0） |
| `GET /v1/prompts` | 可选 `tenant_id` |
| `GET /v1/rag/documents` | 必填 `project_id`；可选 `limit` |
| `GET /v1/mcp/tools`（gateway） | 可选 `server_id`、`tenant_id` |

参数名与默认值已冻结；新增筛选参数属 additive 变更，须登记后实施。

## 5. 幂等请求头（REQ-5.2，可选，已冻结）

控制面 JSON 写请求（POST/PUT/DELETE）可携带：

```
Idempotency-Key: <1-128 位字母/数字/._:->
```

- 同键 + 同请求体：返回首次响应，响应头 `X-Idempotent-Replay: true`，不重复执行。
- 同键 + 不同请求体：`409`，`type=idempotency_error`。
- 同键的上一个请求仍在处理：`409`，稍后重试。
- 业务失败（非 2xx）会释放该键，允许修正后重试。
- 记录落库（重启不失效）；multipart 上传（RAG 文档）不走该机制，按内容哈希天然去重。
- 未携带该头的旧客户端行为不变；路径与鉴权方式均未改变。
- 已冻结（2026-11-17）：头名、冲突判定、`X-Idempotent-Replay` 与上述成功语义不再变更。

## 6. 错误体（已冻结）

两应用统一返回 OpenAI 兼容错误体：

```json
{"error":{"message":"中文说明","type":"authorization_error","param":null,"code":"403"}}
```

常见错误：

| HTTP | type | 含义 |
|---|---|---|
| 401 | `auth_error` | 缺少或无效凭证 |
| 403 | `authorization_error` | 凭证类型不匹配（master 打数据面 / 虚拟 Key 打 console）或越权 |
| 403 | `key_model_access_denied` | 虚拟 Key 无权使用该模型 |
| 403 | `tenant_access_denied` | 请求租户/项目超出 Key 绑定 |
| 404 | `not_found` | 当前应用未提供该路径（跨面访问） |
| 409 | `idempotency_error` | 幂等键冲突或同键请求仍在处理 |
| 409 | `conflict_error` | 同名资源已存在且配置不同（如 MCP Server） |

错误体结构与上表错误码已冻结（2026-11-17）；新增错误码须登记并说明语义。

## 7. 未实现条目（已登记，本期不动手）

> 本节条目不属于已生效契约：§7.1 只登记端点缺口，路径与字段待实现时另行冻结；
> §7.2 的字段清单已定（2026-11-17），但代码尚未实现，客户端必须容忍字段缺失。

### 7.1 端点缺口（仅登记）

| 缺口 | 用途 | 拟定归属 |
|---|---|---|
| 项目列表 | 管理台选择租户下的项目 | console |
| 文档切片列表 | 按 `doc_id` 分页查看切片 | console |
| 单 Key 查询 | 按 `key_hash` 查询 Key 详情 | console |
| Trace 列表 | 按租户/时间分页列出 trace | console |
| 控制台会话端点 | 登录 / 会话探测 / 退出；后端下发 httpOnly + Secure + SameSite=Lax 会话 cookie，前端不接触 token | console |

以上端点均未实现（当前访问返回 404），本期不动手；路径与字段留待实现需求时冻结。

### 7.2 字段级修订（方向已冻结，未实现）

| 目标接口 | 修订 | 用途 | 状态 |
|---|---|---|---|
| `GET /v1/admin/keys` | Key 列表补 `alias`、`metadata`、`rpm_limit`、`tpm_limit`、`max_budget`、`budget_duration`；支持按状态与模型筛选 | 管理台 Key 列表免逐条查详情 | 未实现 |
| `GET /v1/admin/models` | 模型列表补 `description`、价格、更新时间；接收筛选参数 | 模型目录展示与筛选 | 未实现 |
| `GET /v1/prompts/{name}`、`GET /v1/prompts/{name}/versions`、`POST /v1/prompts/{name}/publish` | 补 `tenant_id` 参数（REQ-5.6；响应已含 `tenant_id`） | master 可为指定租户读取/发布 Prompt | 未实现 |
| `/v1/admin/project-authorizations` 列表 | 维持「后续页面」，不阻塞首版 | 项目授权管理 | 未实现 |

字段名与语义已定，后续实现须另立需求、按本表验收并回填状态。

### 7.3 控制台会话方向（端点未实现）

管理员账号 + httpOnly 短期会话 cookie（SameSite=Lax），不使用 token 方案；
master key 保持配置常量，不引入数据库化或有效期。端点为 §7.1 缺口，本期不动手。
