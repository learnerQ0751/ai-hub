# REQ-2.4：Prompt 模板管理

- 日期：2026-11-03
- 状态：已完成
- 关联规格：无（需求卡 `11-项目7-第2期-MCP与Prompt中心.md` §REQ-2.4 + `18-项目7-第2期需求拆解.md` 2.4.1-2.4.4）
- 依赖需求：无（可与 2.1 并行）
- 需求来源：外部需求卡 REQ-2.4
- 提交：04570a7

## 1. 是什么

Prompt 散落在代码里无法版本化、无法灰度回滚。本需求提供 Prompt 模板集中管理：
创建、版本追加、发布/回滚（同一 name 仅一个 PUBLISHED）、变量声明校验（`{{var}}`
必须与 `variables` 一致）、内容安全与租户隔离。

## 2. 有什么用

- 业务价值：Prompt 中心的"配置面"，支撑 2.5 渲染接入与后续灰度实验
- 对应参照行为（B-15，LiteLLM 实测）：
  - LiteLLM：POST 同名自动追加版本、PATCH 原地覆盖、无发布/回滚概念、缺变量渲染为空
  - 本方案：版本只追加、DRAFT/PUBLISHED/ARCHIVED 状态机、发布事务、缺变量 400——比 LiteLLM 更严格（需求卡要求）
- 面试叙事点：版本状态机 + 发布事务；模板变量静态校验；纯文本渲染（防注入）

## 3. 怎么实现的

- 分层落点：`common/prompt`（PromptStatus）、`dal`（PromptTemplateDO/Mapper/V5 表）、`manager/prompt`（PromptManager：版本/发布原子操作 + JSON）、`integration/prompt`（SimpleTemplateRenderer：变量提取/校验/渲染）、`api`（PromptService + DTO）、`service`（PromptServiceImpl：校验/状态机/@Transactional 发布）、`start`（PromptController + AuthFilter 管理面规则）
- 核心类（≤3）：`PromptServiceImpl`（校验/状态机/发布回滚事务）、`PromptManager`（版本与发布原子 SQL）、`SimpleTemplateRenderer`（`{{var}}` 提取/一致性校验/单次替换）
- 请求链路（≤8 行）：

```text
POST /v1/prompts
→ AuthFilter（/v1/prompts 仅 master；render/chat 后缀排除）
→ PromptController → PromptServiceImpl.create
  ① 校验 name（字符集/长度）、content（≤10000、无 sk- 密钥模式）
  ② 变量声明校验：{{var}} 集合 == variables 声明集合（缺失/多余 400）
  ③ nextVersion → 插入 DRAFT
发布 POST /v1/prompts/{name}/publish（@Transactional）：
  lockByName（SELECT ... FOR UPDATE）→ 目标版本存在校验（404）
  → PUBLISHED 全部归档 → 目标版本置 PUBLISHED（发布旧版本即回滚）
```

## 4. 影响面

```yaml
capability:
  type: business
  entry_point: "Service"
  interface_agnostic: false
modules:
  add: [common/prompt, dal, manager/prompt, integration/prompt, api, service, start/controller]
  change: [start/filter/AuthFilter（管理面 master-only 规则，排除 render/chat 数据面）]
interfaces:
  add: ["POST /v1/prompts", "POST /v1/prompts/{name}/versions", "GET /v1/prompts", "GET /v1/prompts/{name}", "GET /v1/prompts/{name}/versions", "POST /v1/prompts/{name}/publish"]
  change: []
  remove: []
tables:
  add: []
  change: ["prompt_templates：使用 V5 已建表"]
config: []
depends_on: []
compat: additive
gate: auto
regression: ["AuthFilter master-only 规则（新增排除项）"]
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 版本只追加；PATCH 不允许改写历史版本 | 审计与回滚可信（LiteLLM 原地覆盖是反例） | 2026-11-03 |
| 发布 = 事务内旧 PUBLISHED→ARCHIVED、目标→PUBLISHED | 同一 name 仅一个发布版；发布旧版本即回滚 | 2026-11-03 |
| 变量静态校验：`{{var}}` 集合必须等于 variables 声明集合 | 需求卡 2.4.3；缺失/多余都 400 | 2026-11-03 |
| 纯文本单次替换，不递归渲染 | 防注入/递归展开；需求卡 2.4.4 | 2026-11-03 |
| 内容长度 ≤ 10000；拒绝疑似密钥（sk-…） | 需求卡 2.4.4 安全要求 | 2026-11-03 |
| Prompt 管理仅 master；render/chat（2.5）为数据面 | 管理面与数据面隔离；AuthFilter 精确排除 | 2026-11-03 |

## 6. 执行摘要

### 2026-11-03

- 进展：实现完成（CRUD/版本/发布回滚/变量校验/安全/租户过滤），门禁全绿；真实链路验证通过（v1→v2→回滚 v1、400/404 中文错误）。
- 下一步：REQ-2.5（Prompt 渲染接入网关）。

## 7. 复盘

- 做对了什么：发布用行锁 + 两次状态更新保证"仅一个 PUBLISHED"；版本只追加，审计可信。
- 踩了什么坑：H2/PostgreSQL 对 `SELECT COUNT(*) ... FOR UPDATE` 不支持，改为 `SELECT id ... LIMIT 1 FOR UPDATE`。
- 下次改进：涉及行锁 SQL 先在 PostgreSQL 语义下校验。

## 8. 多实例检查

- [x] 是否有可变成员变量？是否线程安全、跨实例一致？——无状态；版本数据全部在 DB
- [x] 计数器/限流/预算/冷却是否在 Redis/数据库？——版本与状态在 PostgreSQL
- [x] 锁是否分布式？是否有超时、防死锁、幂等？——发布用 DB 行锁（FOR UPDATE），事务自动释放
- [x] 定时任务是否只执行一次？——无
- [x] 本地缓存是否可容忍过期/不一致？——无缓存
