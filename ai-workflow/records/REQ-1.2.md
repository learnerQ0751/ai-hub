# REQ-1.2：模型配置 + 多部署路由

- 日期：2026-10-20
- 状态：已完成
- 关联规格：`specs/002-model-router/`
- 依赖需求：REQ-1.1
- 需求来源：Vault `05-项目7-第1期-LLM网关核心.md` §REQ-1.2
- 接口：GET /models, GET /v1/models
- 提交：e75732e（基础重构 + 需求 1.2 实现）

## 1. 是什么

在单模型转发之上增加两种能力：① 用配置定义"模型别名 → 多部署"；② 同一别名的多个部署按权重分摊流量。对外仍然只暴露别名，不暴露上游供应商细节。

## 2. 有什么用

- 业务价值：一个别名可挂多家供应商/多套部署，为容错（REQ-1.6 fallback）和成本调度打基础。
- 对应参照行为：LiteLLM `model_list` + `routing_strategy: simple-shuffle`（B-02）。
- 面试叙事点：为什么用无状态加权随机而不是平滑轮询；多实例下状态放哪里。

## 3. 怎么实现的

- 分层落点：`common`（配置模型）→ `core/routing`（策略）→ `service`（查询编排）→ `start/controller`。
- 核心类：`WeightedRandomSelector`、`DeploymentSelector`、`ModelServiceImpl`。
- 请求链路：

```text
ChatCompletionCore：取 DeploymentSelector → 按 rpm/tpm 权重随机选部署
→ 别名改写为真实模型 → 调上游 → 响应 model 回写别名

GET /models：ModelController → ModelService → 只返回别名集合
```

## 4. 影响面

```yaml
modules:
  add: [core/routing]
  change: [common, service, start]
interfaces:
  add: ["GET /models", "GET /v1/models"]
  change: ["POST /v1/chat/completions（选择部署）"]
  remove: []
tables:
  add: []
  change: []
config: [gateway.model-list]
depends_on: [REQ-1.1]
compat: additive
gate: auto
regression: [REQ-1.1 单部署行为一致]
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 配置结构对齐 LiteLLM `model_list` | 便于与参照物逐项对照，降低理解成本 | 2026-10-20 |
| 首期实现 `simple-shuffle` + 策略接口 | 满足多部署基础能力，为后续策略留扩展点 | 2026-10-20 |
| `/models` 只暴露别名 | 不泄露上游供应商与密钥配置 | 2026-10-20 |
| 权重取 `rpm`，缺失回退 `tpm`，默认 1 | 对齐 LiteLLM simple-shuffle，规则简单可预测 | 2026-10-20 |
| 同一别名权重字段必须同源，混用启动失败 | 避免量级混用导致路由偏斜，配置错误早暴露 | 2026-10-20 |
| 无状态加权随机 | 不保存轮询历史，多实例部署天然安全 | 2026-10-20 |
| 路由选择抽象为 `DeploymentSelector` | 为 least-busy 等策略预留扩展点 | 2026-10-20 |
| 多模块分层（对齐监管报送）：9 模块 | 用户评审指正：单模块包分层不符合公司规范 | 2026-10-20 |

## 6. 执行摘要

### 2026-10-20

- 按 spec-kit 流程完成 specify → clarify → plan → tasks → implement，产物在 `specs/002-model-router/`。
- TDD 实现：`Deployment` 增加 `rpm`/`tpm` 与启动校验；新增 `WeightedRandomSelector`、`ModelService`、`ModelController`、`ModelListResDTO`。
- 测试：45 个，覆盖配置绑定、混用/部分配置启动失败、权重比例、单部署、空部署、`/models` 不泄露、端到端路由。
- `mvn -Pquality verify` 通过；集成测试使用 MockWebServer。
- 2026-10-20 基础重构：六层包；`SmoothWeightedSelector` → `WeightedRandomSelector`；Spring Boot 4.1.1 / LangChain4j 1.22.0 / Redis。
- 2026-10-20 多模块重构：Maven 9 模块；start 收口入口层；api 接口契约、service 实现；core 编排；JSON 协议改写下沉 integration；45 测试全绿、真实 DeepSeek 冒烟 200。
- 待办：理解验收（见 `acceptance/REQ-1.2.md`）。

## 7. 复盘

- 做对了：先澄清权重语义与轮询方式，避免实现返工；用 MockWebServer 替代真实上游做端到端轮询验证。
- 踩坑：`@ConfigurationProperties` 的 `@PostConstruct` 时序；SpotBugs record 暴露告警；`TestRestTemplate` 默认 `text/plain`；Spring Boot 4 默认 Jackson 3 与业务 Jackson 2 混用。
- 下次改进：配置文档给出「同源」正反例；错误信息附带别名定位；严格平滑轮询如需用 Redis。

## 8. 多实例检查

- [x] 选择器无状态（加权随机，不保存轮询历史）
- [x] 无计数器/限流/预算/冷却（REQ-1.4 起引入 Redis）
- [x] 无本地锁
- [x] 无定时任务
- [x] 无本地缓存承载业务状态

## 归因（可选）

- 提交：e75732e
- AI/人工行数：—（多模块重构记录见 STATUS/提交历史）
