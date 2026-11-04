# 项目状态

<!-- STATUS:GENERATED:START -->
> 本区块由 `ai-workflow/scripts/status.ps1` 生成；手工备注写在下方。

**最后更新**：2026-11-04 08:58

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
| REQ-2.2 | MCP 工具发现与聚合 | 已完成 | additive | 已验收（2026-11-03）（7/7） | REQ-2.1 | 384f2b5 |
| REQ-2.3 | MCP 调用代理 | 已完成 | extension | 已验收（2026-11-03）（7/7） | REQ-2.1、REQ-2.2 | 5ba30bc |
| REQ-2.4 | Prompt 模板管理 | 已完成 | additive | 已验收（2026-11-03）（7/7） | 无（可与 2.1 并行） | 04570a7 |
| REQ-2.5 | Prompt 渲染接入网关 | 已完成 | additive | 已验收（2026-11-03）（4/4） | REQ-2.4、REQ-1.1 | 96a6b29 |
| REQ-2.6 | Agent 适配层与 MCP Server 暴露 | 已完成 | additive | 已验收（2026-11-03）（4/4） | REQ-2.1、REQ-2.2、REQ-2.3、REQ-2.5 | 3dd2905 |
| REQ-2.7 | 网关统一 MCP 入口（MCP Client 连接池对外形态） | 已完成 | additive | 已验收（2026-11-04）（4/4） | REQ-2.1、REQ-2.2、REQ-2.3 | — |
| REQ-2.8 | chat 入口支持 MCP 工具自动调用 | 已完成 | additive | 已验收（2026-11-04）（4/4） | REQ-2.7、REQ-1.1 | — |

## 接口清单（来自 Controller）

| 方法 | 路径 | Handler | 涉及需求 |
|---|---|---|---|
| POST | /chat/completions | ChatCompletionController#chat | REQ-1.1, REQ-1.2, REQ-1.3, REQ-2.8 |
| POST | /key/block | KeyController#block | REQ-1.3 |
| POST | /key/generate | KeyController#generate | REQ-1.3 |
| GET | /key/info | KeyController#info | REQ-1.3, REQ-1.5 |
| POST | /key/unblock | KeyController#unblock | REQ-1.3 |
| GET | /models | ModelController#listModels | REQ-1.2 |
| POST | /v1/chat/completions | ChatCompletionController#chat | REQ-1.1, REQ-1.2, REQ-1.3, REQ-2.8 |
| GET | /v1/mcp/servers | McpServerController#list | REQ-2.1 |
| POST | /v1/mcp/servers | McpServerController#register | REQ-2.1 |
| DELETE | /v1/mcp/servers/{serverId} | McpServerController#remove | REQ-2.1 |
| POST | /v1/mcp/servers/{serverId}/reprobe | McpServerController#reprobe | REQ-2.1 |
| GET | /v1/mcp/tools | McpToolController#list | REQ-2.2, REQ-2.3 |
| POST | /v1/mcp/tools/{toolName}:call | McpCallController#call | REQ-2.3 |
| POST | /v1/mcp/tools/refresh | McpToolController#refresh | REQ-2.2 |
| GET | /v1/models | ModelController#listModels | REQ-1.2 |
| GET | /v1/prompts | PromptController#list | REQ-2.4, REQ-2.5 |
| POST | /v1/prompts | PromptController#create | REQ-2.4, REQ-2.5 |
| GET | /v1/prompts/{name} | PromptController#get | REQ-2.4, REQ-2.5 |
| POST | /v1/prompts/{name}/chat | PromptRuntimeController#chat | REQ-2.5 |
| POST | /v1/prompts/{name}/publish | PromptController#publish | REQ-2.4 |
| POST | /v1/prompts/{name}/render | PromptRuntimeController#render | REQ-2.5 |
| GET | /v1/prompts/{name}/versions | PromptController#versions | REQ-2.4 |
| POST | /v1/prompts/{name}/versions | PromptController#createVersion | REQ-2.4 |

## 数据表速查（语义来自 docs/DATA-MODEL.md，DDL 校验）

### 网关与鉴权

| 表 | 用途 | 关键关系 | 状态 | 涉及需求 | DDL |
|---|---|---|---|---|---|
| api_keys | 虚拟 Key：SHA-256 哈希、模型白名单、封禁、预算/限流字段（max_budget、budget_duration、rpm/tpm）、spend | 1:N call_logs（key_id） | active | REQ-1.3, REQ-1.5, REQ-1.3 / REQ-1.4 | ✓ |
| call_logs | 调用日志与 Token 计量：request_id / key_id / model_alias / deployment_model / usage / cost / latency / status / error_type / retry_count / fallback_used / served_alias / call_type（CHAT/MCP/PROMPT）/ target_name | N:1 api_keys（key_id，弱关联；master 调用为 NULL） | active | REQ-1.5, REQ-2.3, REQ-1.5 / REQ-1.4 / REQ-1.6 / REQ-2.3 | ✓ |

### MCP 与 Prompt

| 表 | 用途 | 关键关系 | 状态 | 涉及需求 | DDL |
|---|---|---|---|---|---|
| mcp_servers | MCP Server 注册：server_id（UUID）/ name（唯一、禁 "__"）/ transport / endpoint / command / args / status（ACTIVE/DOWN）/ last_health_at / metadata（description、lastError） | 独立配置类表；工具目录为运行时缓存（REQ-2.2） | active | REQ-2.1 | ✓ |
| prompt_templates | Prompt 模板与版本：name / version / content / variables / status（DRAFT/PUBLISHED/ARCHIVED） | 同 name 多版本（name+version 唯一） | active | REQ-2.4 | ✓ |

## 配置（application.yml 顶层键）

- `spring`
- `mybatis`
- `server`
- `logging`
- `gateway`

## 变更矩阵（接口 / 表 → 涉及需求）

| 对象 | 涉及需求 |
|---|---|
| api_keys | REQ-1.3, REQ-1.5 |
| call_logs | REQ-1.5 |
| call_logs：使用 V5 已有 call_type/target_name 列 | REQ-2.3 |
| DELETE /v1/mcp/servers/{serverId} | REQ-2.1 |
| GET /key/info | REQ-1.3, REQ-1.5 |
| GET /models | REQ-1.2 |
| GET /v1/mcp/servers | REQ-2.1 |
| GET /v1/mcp/tools | REQ-2.2 |
| GET /v1/models | REQ-1.2 |
| GET /v1/prompts | REQ-2.4 |
| GET /v1/prompts/{name} | REQ-2.4 |
| GET /v1/prompts/{name}/versions | REQ-2.4 |
| mcp_servers | REQ-2.1 |
| POST /chat/completions | REQ-1.1, REQ-1.3 |
| POST /key/block | REQ-1.3 |
| POST /key/generate | REQ-1.3 |
| POST /key/unblock | REQ-1.3 |
| POST /mcp | REQ-2.6 |
| POST /mcp：tools/list 合并注册工具；tools/call 支持聚合工具路由 | REQ-2.7 |
| POST /v1/chat/completions | REQ-1.1, REQ-1.2, REQ-1.3 |
| POST /v1/chat/completions：支持 tools:[{type:mcp | REQ-2.8 |
| POST /v1/mcp/servers | REQ-2.1 |
| POST /v1/mcp/servers/{serverId}/reprobe | REQ-2.1 |
| POST /v1/mcp/tools/{toolName}:call | REQ-2.3 |
| POST /v1/mcp/tools/refresh | REQ-2.2 |
| POST /v1/prompts | REQ-2.4 |
| POST /v1/prompts/{name}/chat | REQ-2.5 |
| POST /v1/prompts/{name}/publish | REQ-2.4 |
| POST /v1/prompts/{name}/render | REQ-2.5 |
| POST /v1/prompts/{name}/versions | REQ-2.4 |
| prompt_templates：使用 V5 已建表 | REQ-2.4 |
| server_name}]；流式 + mcp → 400 | REQ-2.8 |

## 模块依赖

- `start → service → core → manager/integration → api → dal → common`（ArchUnit 强制，禁止反向）

## 测试

- 230 个 @Test / 44 个测试类

## 下一步

- 实现 REQ-3.1 文档接入（待实现）→ 需求卡：`12-项目7-第3期-RAG与记忆服务.md`

<!-- STATUS:GENERATED:END -->

## 手工备注

<!-- 可手动补充；脚本只替换上方生成区块 -->