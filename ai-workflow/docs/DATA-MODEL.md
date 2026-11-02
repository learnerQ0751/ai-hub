# 数据模型基线（表速查）

> 人向速查：`STATUS.md` 的「数据表速查」**只读取本文件的「当前表」区**。
> 维护规则：新增/修改表时更新本文件；DDL 真源是 `ai-hub-dal/src/main/resources/db/migration/Vn__*.sql`（Flyway），本文件补充业务语义与关系。
> 规划表仅用于设计参考，不代表已实现，不进入 STATUS。

## 当前表（active，STATUS 只显示本区）

### 网关与鉴权

| 表 | 用途 | 关键关系 | 状态 | 引入需求 |
|---|---|---|---|---|
| api_keys | 虚拟 Key：SHA-256 哈希、模型白名单、封禁、预算/限流字段（max_budget、budget_duration、rpm/tpm）、spend | 1:N call_logs（key_id） | active | REQ-1.3 / REQ-1.4 |
| call_logs | 调用日志与 Token 计量：request_id / key_id / model_alias / deployment_model / usage / cost / latency / status / error_type / retry_count / fallback_used / served_alias | N:1 api_keys（key_id，弱关联；master 调用为 NULL） | active | REQ-1.5 / REQ-1.4 / REQ-1.6 |

## 规划表（planned，仅供设计参考，不进入 STATUS）

### MCP 与 Prompt

| 表 | 用途 | 关键关系 | 状态 | 引入需求 |
|---|---|---|---|---|
| mcp_servers | MCP Server 注册：transport / endpoint / status / last_health_at / metadata | 独立配置类表；工具目录为运行时缓存 | planned | REQ-2.1 |
| prompt_templates | Prompt 模板与版本：name / version / content / variables / status | 同 name 多版本（行内版本序列） | planned | REQ-2.4 |

### RAG 与记忆

| 表 | 用途 | 关键关系 | 状态 | 引入需求 |
|---|---|---|---|---|
| rag_documents | 文档元数据：doc_id / hash / source / project_id | 文档 1:N 切片 | planned | REQ-3.1 |
| rag_chunks | 切片与向量：chunk_id / doc_id / content / vector / status | N:1 rag_documents（doc_id） | planned | REQ-3.1 / REQ-3.2 |
| memories | 记忆：user_id / session_id / content / metadata / TTL | 按 user/session 逻辑分组，无强外键 | planned | REQ-3.5 |

### 治理

| 表 | 用途 | 关键关系 | 状态 | 引入需求 |
|---|---|---|---|---|
| audit_logs | 审计：actor / action / target / before-after（脱敏）/ trace_id | 弱关联 api_keys / prompt_templates / mcp_servers | planned | REQ-4.5 |

## 关系总览（当前）

- `api_keys` 1:N `call_logs`：通过 `call_logs.key_id` 逻辑关联（弱关联，不建外键；master 调用 key_id 为 NULL）。
- 规划关系见上方规划表；实现后迁入「当前表」区并更新本节。

## ER 图

- 表数量 ≥5 时补充 `acceptance/assets/data-model.drawio|svg`，并在本文件与 `DESIGN.md` 引用；当前 2 张表，暂不画。
