# REQ-1.2：模型配置 + 多部署路由

- 日期：2026-10-20
- 状态：已完成
- 关联规格：`specs/002-model-router/`
- 参考：`05-项目7-第1期-LLM网关核心.md` 需求 1.2；`06-LiteLLM行为基线.md`
- 接口：GET /models, GET /v1/models
- 提交：—（待提交）

## 需求

- 背景：项目 7 第 1 期 LLM 网关核心的第二个需求，在需求 1.1 单模型转发之上增加模型配置与多部署路由。
- 范围：
  1. 配置结构对齐 LiteLLM `model_list`：模型别名 → 部署列表，每个部署含 `api-base` / `api-key` / `model`。
  2. 同名多部署负载均衡：实现 `simple-shuffle`（加权轮询，支持 `rpm` / `tpm` 权重），预留策略接口供后续 least-busy / usage-based / latency-based 扩展。
  3. `GET /models` 返回可用模型别名，不暴露上游真实模型名与部署参数。
  4. 单部署行为保持需求 1.1 不变；多部署连续调用可观察到轮询。
- 不做什么：虚拟 Key、限流预算、日志计量、fallback、SSE（后续需求）。

## 验收标准

- [x] 配置两个别名（`chat` / `reasoner`）+ 一个别名双部署；连续调用能观察到轮询（`GatewayRoutingIntegrationTest` 以 3:1 权重验证 12/4 命中）。
- [x] `GET /models` 只返回别名，不暴露上游真实模型名、`api-base`、`api-key`（`ModelControllerTest`、`ModelServiceTest`、集成测试）。
- [x] 新增/修改别名与部署只改 YAML，不改代码（配置绑定测试覆盖）。
- [x] 单部署路径与需求 1.1 行为一致（响应 `model` 回写别名、`usage` 不丢；`ChatCompletionServiceTest`）。
- [x] 单元测试覆盖：配置绑定、权重轮询、未知模型、`/models` 输出（41 个测试）。
- [x] `mvn -Pquality verify` 通过（Checkstyle 0 违规、SpotBugs、ArchUnit、JaCoCo ≥70%）。

## 决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 配置结构对齐 LiteLLM `model_list` | 便于与参照物逐项对照，降低理解成本 | 2026-10-20 |
| 首期实现 `simple-shuffle` + 策略接口 | 满足多部署基础能力，同时为后续策略留扩展点 | 2026-10-20 |
| `/models` 只暴露别名 | 不泄露上游供应商与密钥配置 | 2026-10-20 |
| 权重取 `rpm`，缺失回退 `tpm`，默认 1 | 对齐 LiteLLM simple-shuffle，规则简单可预测 | 2026-10-20 |
| 同一别名权重字段必须同源，混用启动失败 | 避免 rpm/tpm 量级混用导致路由偏斜，配置错误早暴露 | 2026-10-20 |
| 采用确定性平滑加权轮询（SWRR） | 连续请求交错命中，验收可重复、不依赖随机种子 | 2026-10-20 |
| 路由选择抽象为 `DeploymentSelector` | 为 least-busy 等策略预留扩展点 | 2026-10-20 |

## 执行日志

### 2026-10-20

- 按 spec-kit 流程完成 `specify → clarify → plan → tasks → implement`，产物在 `specs/002-model-router/`。
- TDD 实现：`Deployment` 增加 `rpm`/`tpm` 与启动校验；新增 `SmoothWeightedSelector`、`ModelService`、`ModelController`、`ModelListResponse`；`ChatCompletionService` 改为策略选择并记录路由日志。
- 测试：41 个，覆盖配置绑定、混用/部分配置启动失败、1:1 与 3:1 轮询、单部署、空部署列表、`/models` 不泄露、端到端 3:1 路由。
- `mvn -Pquality verify` 通过；端到端集成测试使用 MockWebServer，不依赖外网与真实密钥。
- 待办：本地真实 DeepSeek 冒烟（按 `specs/002-model-router/quickstart.md`）。

## 复盘

- 做对了什么：
  - 先澄清权重语义与轮询方式，避免实现返工。
  - 用 MockWebServer 集成测试替代真实上游，端到端验证轮询且无网络依赖。
- 踩了什么坑：
  - `@ConfigurationProperties` 的 `@PostConstruct` 在绑定完成后执行，混用配置能正确阻止启动。
  - SpotBugs 对 record/构造器注入的暴露告警，需要防御性拷贝 + 既有排除规则。
  - `TestRestTemplate` 默认 `text/plain`，需显式设置 JSON `Content-Type`。
- 下次改进：
  - 配置文档中直接给出「同源」正反例，错误信息附带别名定位。

## 理解验收

- 我学到的 3 条：
  1.
  2.
  3.
- 自测题得分：
- 我亲手改的口子：

## 归因（可选）

- 提交：
- AI/人工行数：
- prompt 可追溯：

## 建议的 spec-kit 输入

```text
实现模型配置与多部署路由：
1. 配置结构对齐 LiteLLM model_list：模型别名 → 部署列表（api-base / api-key / model）。
2. 同名多部署 simple-shuffle（加权轮询，支持 rpm/tpm 权重）+ 预留策略接口。
3. GET /models 只返回别名，不暴露上游真实模型名与部署参数。
4. 验收：chat / reasoner 两个别名 + chat 双部署，连续调用能观察到轮询；/models 正确；单部署行为与需求 1.1 一致。
```
