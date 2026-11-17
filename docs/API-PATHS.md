# ai-hub 接口路径表（前端对接用）

> 版本：REQ-5.1（2026-11-16）。数据面与控制面拆为两个应用，管理台只连 console。
> 契约与 4.4 保持一致：管理端点路径不迁移，隔离手段是「console 只认 admin 凭证」。

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
| GET | `/v1/mcp/tools` | 工具目录（可带查询参数） |
| POST | `/v1/mcp/tools/refresh` | 刷新工具目录缓存 |
| POST | `/mcp` | 原生 MCP JSON-RPC 2.0 端点 |
| POST | `/v1/prompts/{name}/render` | 运行时渲染 Prompt |
| POST | `/v1/prompts/{name}/chat` | 渲染后直接对话 |
| POST | `/v1/rag/search` | 知识库检索 |
| POST | `/v1/rag/answer` | 检索 + 生成回答 |
| POST | `/v1/memory/save`、`/recall`、`/clear` | 记忆写入/召回/删除 |

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

## 5. 幂等请求头（REQ-5.2，可选）

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

## 6. 错误体

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
