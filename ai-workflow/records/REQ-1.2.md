# REQ-1.2：模型配置 + 多部署路由

- 日期：2026-10-20
- 状态：代码完成（待理解验收）
- 关联规格：`specs/002-model-router/`
- 参考：`05-项目7-第1期-LLM网关核心.md` 需求 1.2；`06-LiteLLM行为基线.md`
- 接口：GET /models, GET /v1/models
- 提交：e75732e（基础重构 + 需求 1.2 实现）

## 需求

- 背景：项目 7 第 1 期 LLM 网关核心的第二个需求，在需求 1.1 单模型转发之上增加模型配置与多部署路由。
- 范围：
  1. 配置结构对齐 LiteLLM `model_list`：模型别名 → 部署列表，每个部署含 `api-base` / `api-key` / `model`。
  2. 同名多部署负载均衡：实现 `simple-shuffle`（无状态加权随机，支持 `rpm` / `tpm` 权重），预留策略接口供后续 least-busy / usage-based / latency-based 扩展。
  3. `GET /models` 返回可用模型别名，不暴露上游真实模型名与部署参数。
  4. 单部署行为保持需求 1.1 不变；多部署连续调用可观察到轮询。
- 不做什么：虚拟 Key、限流预算、日志计量、fallback、SSE（后续需求）。

## 验收标准

- [x] 配置两个别名（`chat` / `reasoner`）+ 一个别名双部署；连续 60 次调用中两个部署都被选中，且 3:1 权重的部署命中明显更多（`GatewayRoutingIntegrationTest`）。
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
| `simple-shuffle` 采用无状态加权随机 | 不保存轮询历史，多实例部署天然安全；严格平滑轮询如需实现，状态放 Redis | 2026-10-20 |
| 路由选择抽象为 `DeploymentSelector` | 为 least-busy 等策略预留扩展点 | 2026-10-20 |
| 基础重构为六层包 + Spring Boot 4.1.1 + LangChain4j 1.22.0 + Redis | 与 ai-coder-help 技术栈对齐；多实例/后续限流预算需要 Redis | 2026-10-20 |
| 多模块分层（对齐监管报送）：9 模块、start 收口入口、api 接口+契约、service 实现、core 仅编排、协议适配下沉 integration | 用户评审指正：单模块包分层不符合公司规范；start 包不应放启动类 | 2026-10-20 |

## 执行日志

### 2026-10-20

- 按 spec-kit 流程完成 `specify → clarify → plan → tasks → implement`，产物在 `specs/002-model-router/`。
- TDD 实现：`Deployment` 增加 `rpm`/`tpm` 与启动校验；新增 `WeightedRandomSelector`、`ModelService`、`ModelController`、`ModelListResponse`；`ChatCompletionService` 改为策略选择。
- 测试：41 个，覆盖配置绑定、混用/部分配置启动失败、权重比例、单部署、空部署列表、`/models` 不泄露、端到端路由。
- `mvn -Pquality verify` 通过；端到端集成测试使用 MockWebServer，不依赖外网与真实密钥。
- **2026-10-20 基础重构**：`gateway` 混合包拆为六层（start/api/service/core/integration/common/config）；`SmoothWeightedSelector` 替换为无状态 `WeightedRandomSelector`；升级 Spring Boot 4.1.1、LangChain4j 1.22.0、Redis/Lettuce；补类/方法/关键行注释；41 测试与质量门禁通过。
- **2026-10-20 多模块重构**（对齐监管报送 data-submission）：Maven 9 模块（common/api/manager/dal/integration/core/service/start/tests）；start 收口入口层（启动类根包 + start/controller|filter|error|config）；api 放 Service 接口+契约、service 放 Impl；core 改 `@Component` + BO 编排，JSON 协议改写下沉 integration `OpenAiProtocolAdapter`；配置模型下移 common；JaCoCo 聚合门禁置于 tests 模块；45 测试全绿、真实 DeepSeek 冒烟 200。提交范围 `011327d..412bf17`。
- 待办：理解验收（按新模块结构讲解/5 题/亲手改）。

## 复盘

- 做对了什么：
  - 先澄清权重语义与轮询方式，避免实现返工。
  - 用 MockWebServer 集成测试替代真实上游，端到端验证轮询且无网络依赖。
- 踩了什么坑：
  - `@ConfigurationProperties` 的 `@PostConstruct` 在绑定完成后执行，混用配置能正确阻止启动。
  - SpotBugs 对 record/构造器注入的暴露告警，需要防御性拷贝 + 既有排除规则。
  - 原 `TestRestTemplate` 默认 `text/plain`，已改为 `RestClient` 显式 JSON。
  - Spring Boot 4 默认 Jackson 3，而 LangChain4j/业务代码使用 Jackson 2；需显式提供 Jackson 2 `ObjectMapper`。
- 下次改进：
  - 配置文档中直接给出「同源」正反例，错误信息附带别名定位。
  - 需要严格平滑轮询时，用 Redis 共享状态，不再引入单机 Map。

## 理解验收

- [ ] 理解验收完成（我学到的 3 条 + 5 题自测 + 亲手改一个口子）
- 我学到的 3 条：
  1.
  2.
  3.
- 自测题得分：
- 我亲手改的口子：

## 归因（可选）

- 提交：e75732e
- AI/人工行数：
- prompt 可追溯：

## 建议的 spec-kit 输入

```text
实现模型配置与多部署路由：
1. 配置结构对齐 LiteLLM model_list：模型别名 → 部署列表（api-base / api-key / model）。
2. 同名多部署 simple-shuffle（无状态加权随机，支持 rpm/tpm 权重）+ 预留策略接口。
3. GET /models 只返回别名，不暴露上游真实模型名与部署参数。
4. 验收：chat / reasoner 两个别名 + chat 双部署，连续调用能观察到轮询；/models 正确；单部署行为与需求 1.1 一致。
```
