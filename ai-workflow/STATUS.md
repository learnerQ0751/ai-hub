# 项目状态

<!-- STATUS:GENERATED:START -->
> 本区块由 `ai-workflow/scripts/status.ps1` 生成；手工备注写在下方。

**最后更新**：2026-11-17 12:29

## 需求进度

| REQ | 名称 | 状态 | 兼容级别 | 验收 | 依赖 | 提交 |
|---|---|---|---|---|---|---|
| REQ-1.1 | 统一入口 + 单模型转发（非流式） | 已完成 | additive | 已验收（2026-10-30，理解默认通过）（6/6） | 无 | 60777b0 |
| REQ-1.2 | 模型配置 + 多部署路由 | 已完成 | additive | 已验收（2026-10-30，理解默认通过）（6/6） | REQ-1.1 | e75732e（基础重构 + 需求 1.2 实现） |
| REQ-1.3 | 虚拟 Key | 已完成 | extension | 已验收（2026-10-30，理解默认通过）（6/6） | REQ-1.2 | 881b844 |
| REQ-1.4 | 限流与预算（Redis 实现） | 已完成 | additive | 已验收（2026-11-02，理解默认通过）（7/7） | REQ-1.3、REQ-1.5 | 0257305（初版）+ 1d1154c（网关治理解耦重构）+ b4b8442（Filter 统一治理）+ 7b798a7（删除 ChatExecutor 假出口） |
| REQ-1.5 | 调用日志 + Token 计量 + `/key/info` | 已完成 | additive | 已验收（2026-11-02，理解默认通过）（7/7） | REQ-1.3 | e7edf54 |
| REQ-1.6 | 容错（重试 / fallback / 冷却） | 已完成 | extension | 已验收（2026-11-02，理解默认通过）（8/8） | REQ-1.5 | — |
| REQ-1.7 | 流式（SSE）透传 | 已完成 | extension | 已验收（2026-11-02，理解默认通过）（7/7） | REQ-1.1、REQ-1.6 | — |
| REQ-1.8 | 对外错误信息中文化 | 已完成 | extension | 已验收（2026-11-03）（8/8） | REQ-1.1 ~ 1.7（覆盖全部错误响应） | — |
| REQ-2.1 | MCP Server 注册与发现 | 已完成 | additive | 已验收（2026-11-03）（8/8） | 无（第 2 期首卡） | 02caa77 |
| REQ-2.10 | MCP 客户端资源治理（探活与连接泄漏防护） | 已完成 | additive | 已验收（2026-11-04）（7/7） | REQ-2.1、2.2、2.3 | 360f2f0 |
| REQ-2.2 | MCP 工具发现与聚合 | 已完成 | additive | 已验收（2026-11-03）（7/7） | REQ-2.1 | 384f2b5 |
| REQ-2.3 | MCP 调用代理 | 已完成 | extension | 已验收（2026-11-03）（7/7） | REQ-2.1、REQ-2.2 | 5ba30bc |
| REQ-2.4 | Prompt 模板管理 | 已完成 | additive | 已验收（2026-11-03）（7/7） | 无（可与 2.1 并行） | 04570a7 |
| REQ-2.5 | Prompt 渲染接入网关 | 已完成 | additive | 已验收（2026-11-03）（4/4） | REQ-2.4、REQ-1.1 | 96a6b29 |
| REQ-2.6 | Agent 适配层与 MCP Server 暴露 | 已完成 | additive | 已验收（2026-11-03）（4/4） | REQ-2.1、REQ-2.2、REQ-2.3、REQ-2.5 | 3dd2905 |
| REQ-2.7 | 网关统一 MCP 入口（MCP Client 连接池对外形态） | 已完成 | additive | 已验收（2026-11-04）（4/4） | REQ-2.1、REQ-2.2、REQ-2.3 | 8ba218d |
| REQ-2.8 | chat 入口支持 MCP 工具自动调用 | 已完成 | additive | 已验收（2026-11-04）（5/5） | REQ-2.7、REQ-1.1 | 8ba218d + a484c68（流式形态 A） |
| REQ-3.1 | 文档接入（上传/解析/切分） | 已完成 | additive | 待验收（2026-11-11，实现与自检完成）（11/11） | 无（文档接入是本批次第一个 RAG 需求） | a4bc778 |
| REQ-3.10 | 索引分页加载与限制策略 | 已完成 | additive | 待验收（2026-11-12，实现与自检完成）（11/11） | REQ-3.2、REQ-3.7 | bad76ed |
| REQ-3.2 | Embedding 抽象 + 向量存储 | 已完成 | additive | 待验收（2026-11-11，实现与自检完成）（13/13） | REQ-3.1 | 22d46f1 |
| REQ-3.3 | 检索与重排 | 已完成 | additive | 待验收（2026-11-11，实现与自检完成）（13/13） | REQ-3.2 | 63f2b28 |
| REQ-3.4 | RAG 生成 + 引用溯源 | 已完成 | additive | 待验收（2026-11-11，实现与自检完成）（13/13） | REQ-3.3、REQ-1.1 | 0063c38 |
| REQ-3.5 | 记忆服务（session/user） | 已完成 | additive | 已验收（2026-11-11，理解默认通过）（14/14） | REQ-2.1（需求卡前置；本期实现不感知 MCP，MCP 暴露在 REQ-3.6） | c692b9c |
| REQ-3.6 | RAG/记忆 MCP Server 暴露 | 已完成 | additive | 待验收（2026-11-11，实现与自检完成）（11/11） | REQ-3.1、REQ-3.2、REQ-3.3、REQ-3.4、REQ-3.5、REQ-2.6 | 0dc5e9e |
| REQ-3.7 | Embedding 一等能力（阿里 text-embedding-v3） | 已完成 | additive | 待验收（2026-11-12，实现与自检完成）（15/15） | REQ-3.1、REQ-3.2、REQ-3.3、REQ-3.4 | 647f998 |
| REQ-3.8 | 计费与边界补强（RAG answer 计费 / chunk 推导 / 上传限制） | 已完成 | additive | 待验收（2026-11-12，实现与自检完成）（11/11） | REQ-3.4、REQ-3.7 | 647f998 |
| REQ-3.9 | 文档解析扩展（docx / pptx / xlsx / html） | 已完成 | additive | 待验收（2026-11-12，实现与自检完成）（15/15） | REQ-3.1、REQ-3.8 | b2dca87 |
| REQ-4.1 | 多租户贯穿与隔离 | 已完成（待主人验收） | extension | 待验收（2026-11-16，实现与自检完成）（10/10） | REQ-1.3、REQ-2.1、REQ-2.4、REQ-3.1~3.5 | 38c5d39 |
| REQ-4.2 | 全链路 Trace | 已完成（待主人验收） | additive | 待验收（2026-11-16，实现与自检完成）（9/9） | REQ-1.5、REQ-2.3、REQ-3.4、REQ-4.1 | 674031f |
| REQ-4.3 | 成本与用量统计 | 已完成（待主人验收） | additive | 待验收（2026-11-16，实现与自检完成）（9/9） | REQ-1.5、REQ-4.1 | c6ef262 |
| REQ-4.4 | 管理端（模型/Key/Prompt/MCP/日志） | 已完成（待主人验收） | additive | 待验收（2026-11-16，实现与自检完成）（9/9） | REQ-4.1、REQ-4.3、REQ-4.5（审计在 4.5 接入） | f333e7d |
| REQ-4.5 | 审计日志 + 项目总验收 | 已完成（待主人验收） | additive | 待验收（2026-11-16，实现与自检完成）（12/14） | REQ-4.1~4.4 | a5954a9 |
| REQ-5.1 | 数据面 / 控制面拆分 | 已完成（待主人验收） | breaking | 待验收（2026-11-16，实现与自检完成）（6/6） | REQ-4.1 ~ REQ-4.5 | a3882e5 |
| REQ-5.2 | 控制面幂等与拆分配置收口 | 已完成（待主人验收） | additive | 待验收（2026-11-17，实现与自检完成）（6/6） | REQ-5.1 | 7160b8e |
| REQ-5.3 | BL-1 混合检索（向量 + 字面 RRF） | 已完成（待主人验收） | additive | 待验收（2026-11-17，实现与自检完成）（6/6） | REQ-3.3、REQ-3.7、REQ-4.1 | 2e29b6d |
| REQ-5.4 | BL-3 切片顺序还原与上下文扩展 | 已完成（待主人验收） | extension | 待验收（2026-11-17，实现与自检完成）（6/6） | REQ-3.1、REQ-3.3、REQ-3.4、REQ-3.10、REQ-4.1 | 57bb476 |

## 接口清单（来自 Controller）

| 方法 | 路径 | Handler | 涉及需求 |
|---|---|---|---|
| POST | /chat/completions | ChatCompletionController#chat | REQ-1.1, REQ-1.2, REQ-1.3, REQ-2.8 |
| POST | /embeddings | EmbeddingController#embed | REQ-3.7 |
| POST | /key/block | KeyController#block | REQ-1.3 |
| POST | /key/generate | KeyController#generate | REQ-1.3 |
| GET | /key/info | KeyController#info | REQ-1.3, REQ-1.5 |
| POST | /key/unblock | KeyController#unblock | REQ-1.3 |
| GET | /models | ModelController#listModels | REQ-1.2 |
| GET | /v1/admin/keys | AdminController#listKeys | — |
| POST | /v1/admin/keys/{keyHash}/block | AdminController#block | — |
| POST | /v1/admin/keys/{keyHash}/unblock | AdminController#unblock | — |
| GET | /v1/admin/logs | AdminController#listLogs | — |
| GET | /v1/admin/models | AdminController#listModels | — |
| POST | /v1/admin/models | AdminController#createModel | — |
| DELETE | /v1/admin/models/{alias} | AdminController#deleteModel | — |
| GET | /v1/admin/models/{alias} | AdminController#getModel | — |
| PUT | /v1/admin/models/{alias} | AdminController#updateModel | — |
| POST | /v1/admin/models/{alias}/deployments | AdminController#upsertDeployment | — |
| POST | /v1/admin/project-authorizations | AdminController#authorize | — |
| GET | /v1/audit-logs | AuditLogController#query | REQ-4.5 |
| POST | /v1/chat/completions | ChatCompletionController#chat | REQ-1.1, REQ-1.2, REQ-1.3, REQ-2.8 |
| POST | /v1/embeddings | EmbeddingController#embed | REQ-3.7 |
| GET | /v1/mcp/runtime/stats | McpServerController#runtimeStats | REQ-2.10 |
| GET | /v1/mcp/servers | McpServerController#list | REQ-2.1 |
| POST | /v1/mcp/servers | McpServerController#register | REQ-2.1 |
| DELETE | /v1/mcp/servers/{serverId} | McpServerController#remove | REQ-2.1 |
| POST | /v1/mcp/servers/{serverId}/reprobe | McpServerController#reprobe | REQ-2.1 |
| GET | /v1/mcp/tools | McpToolController#list | REQ-2.2, REQ-2.3 |
| POST | /v1/mcp/tools/{toolName}:call | McpCallController#call | REQ-2.3 |
| POST | /v1/mcp/tools/refresh | McpToolController#refresh | REQ-2.2 |
| POST | /v1/memory/clear | MemoryController#clear | REQ-3.5 |
| POST | /v1/memory/recall | MemoryController#recall | REQ-3.5 |
| POST | /v1/memory/save | MemoryController#save | REQ-3.5 |
| GET | /v1/models | ModelController#listModels | REQ-1.2 |
| GET | /v1/prompts | PromptController#list | REQ-2.4, REQ-2.5 |
| POST | /v1/prompts | PromptController#create | REQ-2.4, REQ-2.5 |
| GET | /v1/prompts/{name} | PromptController#get | REQ-2.4, REQ-2.5 |
| POST | /v1/prompts/{name}/chat | PromptRuntimeController#chat | REQ-2.5 |
| POST | /v1/prompts/{name}/publish | PromptController#publish | REQ-2.4 |
| POST | /v1/prompts/{name}/render | PromptRuntimeController#render | REQ-2.5 |
| GET | /v1/prompts/{name}/versions | PromptController#versions | REQ-2.4 |
| POST | /v1/prompts/{name}/versions | PromptController#createVersion | REQ-2.4 |
| POST | /v1/rag/answer | RagAnswerController#answer | REQ-3.4 |
| GET | /v1/rag/documents | RagDocumentController#list | REQ-3.1, REQ-3.2 |
| POST | /v1/rag/documents | RagDocumentController#upload | REQ-3.1, REQ-3.2 |
| DELETE | /v1/rag/documents/{docId} | RagDocumentController#remove | REQ-3.1, REQ-3.2 |
| POST | /v1/rag/documents/{docId}/reindex | RagDocumentController#reindex | REQ-3.2 |
| POST | /v1/rag/search | RagSearchController#search | REQ-3.3 |
| GET | /v1/traces/{traceId} | TraceController#get | REQ-4.2 |
| GET | /v1/usage | UsageController#query | REQ-4.3 |
| GET | /v1/usage/export | UsageController#export | REQ-4.3 |

## 数据表速查（语义来自 docs/DATA-MODEL.md，DDL 校验）

### 租户与模型目录

| 表 | 用途 | 关键关系 | 状态 | 涉及需求 | DDL |
|---|---|---|---|---|---|
| tenants | 租户：tenant_id（主键）/ name / status / created_at | 1:N projects、api_keys.tenant_id | active | REQ-4.1 | ✓ |
| projects | 项目：tenant_id + project_id（联合主键）/ name / status | N:1 tenants；被 api_keys/rag_documents/memories 引用（逻辑） | active | REQ-4.1 | ✓ |
| project_authorizations | 项目级显式授权：tenant_id / source_project_id / target_project_id / resource_type（MEMORY/KNOWLEDGE/ALL），联合唯一 | 授权源项目访问目标项目；默认隔离 | active | REQ-4.1 | ✓ |
| model_aliases | 别名：alias（唯一）/ tenant_id（空=全局）/ description / fallbacks（JSONB）/ status | 1:N model_alias_deployments、model_alias_keys | active | REQ-4.1 | ✓ |
| model_deployments | 部署实例：deployment_key（唯一）/ api_base / api_key_cipher（AES-GCM 密文）/ upstream_model / status | N:M model_aliases | active | REQ-4.1 | ✓ |
| model_alias_deployments | 别名↔部署：alias_id + deployment_id（联合主键）/ weight / priority | 多对多关系表 | active | REQ-4.1 | ✓ |
| model_alias_keys | 别名↔虚拟 Key：alias_id + key_hash（联合主键） | 多对多关系表 | active | REQ-4.1 | ✓ |

### 可观测性

| 表 | 用途 | 关键关系 | 状态 | 涉及需求 | DDL |
|---|---|---|---|---|---|
| audit_logs | 审计：tenant_id / actor（master 或 Key 哈希）/ action / target / before_state（JSONB 脱敏）/ after_state / trace_id / created_at；只追加，PG 触发器拒绝 UPDATE/DELETE | 弱关联 trace_events（trace_id）与 api_keys/prompt_templates/mcp_servers（target） | active | REQ-3.5, REQ-4.5, REQ-4.5 | ✓ |
| v_usage_daily | 用量/成本实时聚合视图：usage_date / tenant_id / project_id / model_alias / key_id / requests / prompt_tokens / completion_tokens / total_tokens / cost | 直接从 call_logs 聚合；与逐条日志同口径 | active | REQ-4.3 | — |
| trace_events | 全链路分段事件：trace_id（=request_id）/ tenant_id / project_id / key_id / segment（gateway/model/rag/mcp/answer）/ name / status / latency_ms / payload（JSONB 摘要）/ created_at | 弱关联 call_logs（trace_id）；按 trace_id 聚合成一次请求轨迹 | active | REQ-4.2 | ✓ |
| idempotency_keys | 控制面幂等键：idempotency_key（唯一）/ request_hash（SHA-256(method+path+body)）/ response_status / response_content_type / response_body / created_at / updated_at | 独立表；同键同请求重放首次响应，重启不失效 | active | REQ-5.2 | ✓ |

### 网关与鉴权

| 表 | 用途 | 关键关系 | 状态 | 涉及需求 | DDL |
|---|---|---|---|---|---|
| api_keys | 虚拟 Key：SHA-256 哈希、模型白名单、封禁、预算/限流字段（max_budget、budget_duration、rpm/tpm）、spend、tenant_id / project_id（绑定租户与项目，V10） | 1:N call_logs（key_id）；绑定 tenant/project | active | REQ-1.3, REQ-1.5, REQ-4.1, REQ-1.3 / REQ-1.4 / REQ-4.1 | ✓ |
| call_logs | 调用日志与 Token 计量：request_id / key_id / model_alias / deployment_model / usage / cost / latency / status / error_type / retry_count / fallback_used / served_alias / call_type（CHAT/MCP/PROMPT/MEMORY/RAG/EMBEDDING）/ target_name / tenant_id / project_id / trace_id（V10） | N:1 api_keys（key_id，弱关联；master 调用为 NULL） | active | REQ-1.5, REQ-2.3, REQ-4.1, REQ-1.5 / REQ-1.4 / REQ-1.6 / REQ-2.3 / REQ-3.5 / REQ-3.1 / REQ-3.7 | ✓ |

### MCP 与 Prompt

| 表 | 用途 | 关键关系 | 状态 | 涉及需求 | DDL |
|---|---|---|---|---|---|
| mcp_servers | MCP Server 注册：server_id（UUID）/ name（租户内唯一、禁 "__"）/ transport / endpoint / command / args / status（ACTIVE/DOWN）/ last_health_at / metadata（description、lastError） | 独立配置类表；工具目录为运行时缓存（REQ-2.2） | active | REQ-2.1 | ✓ |
| prompt_templates | Prompt 模板与版本：name / version / content / variables / status（DRAFT/PUBLISHED/ARCHIVED）/ tenant_id | 租户内同 name 多版本（tenant_id+name+version 唯一） | active | REQ-2.4, REQ-2.4 / REQ-4.1 | ✓ |

### RAG 与记忆

| 表 | 用途 | 关键关系 | 状态 | 涉及需求 | DDL |
|---|---|---|---|---|---|
| memories | 记忆：memory_id（UUID）/ tenant_id / project_id / user_id / session_id / memory_type（SESSION/USER）/ content / metadata（JSONB）/ expires_at；TTL 查询时过滤 | 按 tenant + project + user（+ session）逻辑分组，无强外键；用户级记忆可跨项目共享（配置开启） | active | REQ-3.5 | ✓ |
| memory_audit_logs | 物理删除审计：audit_id / 作用域 / operation（CLEAR）/ deleted_count / memory_ids（JSONB，最多 1000 条）/ operator_key / reason | 与 memories 通过 audit_id 独立；保存删除快照，不做外键 | active | REQ-3.5 | ✓ |
| rag_documents | 文档元数据：doc_id / project_id / source / content_type / size_bytes / content_hash / raw_content（BYTEA 原始文件）/ tenant_id / chunk_count / index_status（PENDING/PROCESSING/READY/FAILED）/ embedding_model / embedding_dimension / embedding_duration_ms / embedding_cost（按 embedding 模型单价计算；本地/确定性为 0）/ error_message | 文档 1:N rag_chunks（doc_id 逻辑关联）；租户+项目内 content_hash 唯一 | active | REQ-3.1, REQ-3.2, REQ-3.1 / REQ-3.2 / REQ-3.7 / REQ-4.1 | ✓ |
| rag_chunks | 切片：chunk_id（doc_id-序号）/ doc_id / tenant_id / project_id / chunk_index / content / char_count / source / index_status / embedding_model / embedding_dimension / error_message | N:1 rag_documents（doc_id 逻辑关联）；向量在 rag_vectors（REQ-3.2） | active | REQ-3.1, REQ-3.2, REQ-3.1 / REQ-3.2 | ✓ |
| rag_vectors | 切片向量：chunk_id / doc_id / tenant_id / project_id / embedding_model / embedding_dimension / embedding vector(1024)；低维模型零填充；HNSW cosine 索引（V9，REQ-3.7） | 1:1 rag_chunks（chunk_id 唯一）；按 tenant_id + project_id + 模型版本隔离 | active | REQ-3.2, REQ-3.7, REQ-3.2 / REQ-3.7 / REQ-4.1 | ✓ |

## 配置（application.yml 顶层键）

- —

## 变更矩阵（接口 / 表 → 涉及需求）

| 对象 | 涉及需求 |
|---|---|
| [api_keys(+tenant_id | REQ-4.1 |
| [tenants | REQ-4.1 |
| api_keys | REQ-1.3, REQ-1.5 |
| audit_logs | REQ-4.5 |
| call_logs | REQ-1.5 |
| call_logs(+project_id | REQ-4.1 |
| call_logs：使用 V5 已有 call_type/target_name 列 | REQ-2.3 |
| DELETE /v1/mcp/servers/{serverId} | REQ-2.1 |
| DELETE /v1/rag/documents/{docId} | REQ-3.1 |
| GET /key/info | REQ-1.3, REQ-1.5 |
| GET /models | REQ-1.2 |
| GET /v1/audit-logs | REQ-4.5 |
| GET /v1/mcp/runtime/stats | REQ-2.10 |
| GET /v1/mcp/servers | REQ-2.1 |
| GET /v1/mcp/tools | REQ-2.2 |
| GET /v1/models | REQ-1.2 |
| GET /v1/prompts | REQ-2.4 |
| GET /v1/prompts/{name} | REQ-2.4 |
| GET /v1/prompts/{name}/versions | REQ-2.4 |
| GET /v1/rag/documents | REQ-3.1 |
| GET /v1/traces/{traceId} | REQ-4.2 |
| GET /v1/usage | REQ-4.3 |
| GET /v1/usage/export | REQ-4.3 |
| idempotency_keys | REQ-5.2 |
| mcp_servers | REQ-2.1 |
| memories | REQ-3.5 |
| memory_audit_logs | REQ-3.5 |
| model_aliases | REQ-4.1 |
| model_deployments | REQ-4.1 |
| POST /chat/completions | REQ-1.1, REQ-1.3 |
| POST /key/block | REQ-1.3 |
| POST /key/generate | REQ-1.3 |
| POST /key/unblock | REQ-1.3 |
| POST /mcp | REQ-2.6 |
| POST /mcp：tools/list 合并注册工具；tools/call 支持聚合工具路由 | REQ-2.7 |
| POST /mcp：新增 MCP 工具 ingest_document / search_knowledge / rag_answer / save_memory / recall_memory / clear_memory | REQ-3.6 |
| POST /v1/chat/completions | REQ-1.1, REQ-1.2, REQ-1.3 |
| POST /v1/chat/completions：支持 tools:[{type:mcp | REQ-2.8 |
| POST /v1/embeddings | REQ-3.7 |
| POST /v1/mcp/servers | REQ-2.1 |
| POST /v1/mcp/servers/{serverId}/reprobe | REQ-2.1 |
| POST /v1/mcp/tools/{toolName}:call | REQ-2.3 |
| POST /v1/mcp/tools/refresh | REQ-2.2 |
| POST /v1/memory/clear | REQ-3.5 |
| POST /v1/memory/recall | REQ-3.5 |
| POST /v1/memory/save | REQ-3.5 |
| POST /v1/prompts | REQ-2.4 |
| POST /v1/prompts/{name}/chat | REQ-2.5 |
| POST /v1/prompts/{name}/publish | REQ-2.4 |
| POST /v1/prompts/{name}/render | REQ-2.5 |
| POST /v1/prompts/{name}/versions | REQ-2.4 |
| POST /v1/rag/answer | REQ-3.4 |
| POST /v1/rag/documents | REQ-3.1 |
| POST /v1/rag/documents/{docId}/reindex | REQ-3.2 |
| POST /v1/rag/search | REQ-3.3 |
| project_authorizations | REQ-4.1 |
| project_id) | REQ-4.1 |
| projects | REQ-4.1 |
| prompt_templates：使用 V5 已建表 | REQ-2.4 |
| rag_chunks | REQ-3.1, REQ-3.2 |
| rag_documents | REQ-3.1, REQ-3.2 |
| rag_vectors | REQ-3.2, REQ-3.7 |
| server_name}]；流式 + mcp → 400 | REQ-2.8 |
| trace_events | REQ-4.2 |
| trace_id) | REQ-4.1 |
| v_usage_daily | REQ-4.3 |
| 响应头新增 X-Trace-Id | REQ-4.2 |

## 模块依赖

- `start → service → core → manager/integration → api → dal → common`（ArchUnit 强制，禁止反向）

## 测试

- 496 个 @Test / 88 个测试类

## 下一步

- 验收 REQ-3.1 文档接入（上传/解析/切分） → 打开 `acceptance/REQ-3.1.md`
- 验收 REQ-3.10 索引分页加载与限制策略 → 打开 `acceptance/REQ-3.10.md`
- 验收 REQ-3.2 Embedding 抽象 + 向量存储 → 打开 `acceptance/REQ-3.2.md`
- 验收 REQ-3.3 检索与重排 → 打开 `acceptance/REQ-3.3.md`
- 验收 REQ-3.4 RAG 生成 + 引用溯源 → 打开 `acceptance/REQ-3.4.md`
- 验收 REQ-3.6 RAG/记忆 MCP Server 暴露 → 打开 `acceptance/REQ-3.6.md`
- 验收 REQ-3.7 Embedding 一等能力（阿里 text-embedding-v3） → 打开 `acceptance/REQ-3.7.md`
- 验收 REQ-3.8 计费与边界补强（RAG answer 计费 / chunk 推导 / 上传限制） → 打开 `acceptance/REQ-3.8.md`
- 验收 REQ-3.9 文档解析扩展（docx / pptx / xlsx / html） → 打开 `acceptance/REQ-3.9.md`
- 验收 REQ-4.1 多租户贯穿与隔离 → 打开 `acceptance/REQ-4.1.md`
- 验收 REQ-4.2 全链路 Trace → 打开 `acceptance/REQ-4.2.md`
- 验收 REQ-4.3 成本与用量统计 → 打开 `acceptance/REQ-4.3.md`
- 验收 REQ-4.4 管理端（模型/Key/Prompt/MCP/日志） → 打开 `acceptance/REQ-4.4.md`
- 验收 REQ-4.5 审计日志 + 项目总验收 → 打开 `acceptance/REQ-4.5.md`
- 验收 REQ-5.1 数据面 / 控制面拆分 → 打开 `acceptance/REQ-5.1.md`
- 验收 REQ-5.2 控制面幂等与拆分配置收口 → 打开 `acceptance/REQ-5.2.md`
- 验收 REQ-5.3 BL-1 混合检索（向量 + 字面 RRF） → 打开 `acceptance/REQ-5.3.md`
- 验收 REQ-5.4 BL-3 切片顺序还原与上下文扩展 → 打开 `acceptance/REQ-5.4.md`

<!-- STATUS:GENERATED:END -->

## 手工备注

<!-- 可手动补充；脚本只替换上方生成区块 -->