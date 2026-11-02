# 项目状态

<!-- STATUS:GENERATED:START -->
> 本区块由 `ai-workflow/scripts/status.ps1` 生成；手工备注写在下方。

**最后更新**：2026-11-02 15:58

## 需求进度

| REQ | 名称 | 状态 | 兼容级别 | 验收 | 依赖 | 提交 |
|---|---|---|---|---|---|---|
| REQ-1.1 | 统一入口 + 单模型转发（非流式） | 已完成 | additive | 已验收（2026-10-30，理解默认通过）（6/6） | 无 | 60777b0 |
| REQ-1.2 | 模型配置 + 多部署路由 | 已完成 | additive | 已验收（2026-10-30，理解默认通过）（6/6） | REQ-1.1 | e75732e（基础重构 + 需求 1.2 实现） |
| REQ-1.3 | 虚拟 Key | 已完成 | extension | 已验收（2026-10-30，理解默认通过）（6/6） | REQ-1.2 | 881b844 |
| REQ-1.4 | 限流与预算（Redis 实现） | 已完成 | additive | 已验收（2026-11-02，理解默认通过）（7/7） | REQ-1.3、REQ-1.5 | 0257305（初版）+ 1d1154c（网关治理解耦重构）+ b4b8442（Filter 统一治理） |
| REQ-1.5 | 调用日志 + Token 计量 + `/key/info` | 已完成 | additive | 已验收（2026-11-02，理解默认通过）（7/7） | REQ-1.3 | e7edf54 |

## 接口清单（来自 Controller）

| 方法 | 路径 | Handler | 涉及需求 |
|---|---|---|---|
| POST | /chat/completions | ChatCompletionController#chat | REQ-1.1, REQ-1.2, REQ-1.3 |
| POST | /key/block | KeyController#block | REQ-1.3 |
| POST | /key/generate | KeyController#generate | REQ-1.3 |
| GET | /key/info | KeyController#info | REQ-1.3, REQ-1.5 |
| POST | /key/unblock | KeyController#unblock | REQ-1.3 |
| GET | /models | ModelController#listModels | REQ-1.2 |
| POST | /v1/chat/completions | ChatCompletionController#chat | REQ-1.1, REQ-1.2, REQ-1.3 |
| GET | /v1/models | ModelController#listModels | REQ-1.2 |

## 数据表速查（语义来自 docs/DATA-MODEL.md，DDL 校验）

### 网关与鉴权

| 表 | 用途 | 关键关系 | 状态 | 涉及需求 | DDL |
|---|---|---|---|---|---|
| api_keys | 虚拟 Key：SHA-256 哈希、模型白名单、封禁、预算/限流字段（max_budget、budget_duration、rpm/tpm）、spend | 1:N call_logs（key_id） | active | REQ-1.3, REQ-1.5, REQ-1.3 / REQ-1.4 | ✓ |
| call_logs | 调用日志与 Token 计量：request_id / key_id / model_alias / deployment_model / usage / cost / latency / status / error_type | N:1 api_keys（key_id，弱关联；master 调用为 NULL） | active | REQ-1.5, REQ-1.5 / REQ-1.4 | ✓ |

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
| GET /key/info | REQ-1.3, REQ-1.5 |
| GET /models | REQ-1.2 |
| GET /v1/models | REQ-1.2 |
| POST /chat/completions | REQ-1.1, REQ-1.3 |
| POST /key/block | REQ-1.3 |
| POST /key/generate | REQ-1.3 |
| POST /key/unblock | REQ-1.3 |
| POST /v1/chat/completions | REQ-1.1, REQ-1.2, REQ-1.3 |

## 模块依赖

- `start → service → core → manager/integration → api → dal → common`（ArchUnit 强制，禁止反向）

## 测试

- 155 个 @Test / 30 个测试类

## 下一步

- 实现 REQ-1.6 容错（重试/fallback/冷却）（待实现）→ 需求卡：`05-项目7-第1期-LLM网关核心.md`

<!-- STATUS:GENERATED:END -->

## 手工备注

<!-- 可手动补充；脚本只替换上方生成区块 -->