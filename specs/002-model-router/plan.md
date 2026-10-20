# Implementation Plan: Model Configuration & Multi-Deployment Routing (需求 1.2)

**Branch**: `002-model-router` | **Date**: 2026-10-20 | **Spec**: `specs/002-model-router/spec.md`

**Input**: Feature specification from `specs/002-model-router/spec.md`

## Summary

在需求 1.1 的单模型转发之上增加：部署级权重配置（`rpm` / `tpm`）、同一别名多部署的确定性平滑加权轮询、以及只暴露别名的 `GET /models` 与 `GET /v1/models`。路由选择抽象为 `DeploymentSelector`，本期实现 `SmoothWeightedSelector`；配置在启动时校验，混用权重字段直接失败；单部署路径保持 1.1 行为。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.11（Web、Validation、Configuration Properties）、Jackson、Spring RestClient（沿用 1.1）

**Storage**: 无（配置来自 YAML；轮询状态为进程内内存）

**Testing**: JUnit 5、Spring MockMvc、MockWebServer、ArchUnit、JaCoCo

**Target Platform**: Windows 11 本地开发；后续 Linux 部署

**Project Type**: Web service（单模块 Maven 项目）

**Performance Goals**: 单次选择 O(部署数)；部署数 <10，不引入额外 I/O

**Constraints**: 硬门禁 `mvn -Pquality verify`；密钥不入库；不做热加载；单实例内轮询状态

**Scale/Scope**: 单机；别名约 10 个，每别名部署 <10；7 个需求中的第 2 个

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结果 |
|---|---|---|
| I. Security First | `/models` 只输出别名；路由日志不含 `api-key` | PASS |
| II. Test-First & Verification | 配置绑定、轮询、`/models` 先写失败测试；硬门禁 | PASS |
| III. Layered Boundaries | Controller → Service → Selector；`config` 不依赖 `gateway` | PASS |
| IV. OpenAI Compatibility & Observability | `/models` 兼容列表；选中部署写入日志 | PASS |
| V. Simplicity & Maintainability | 单接口 + 单实现；不引入数据库/随机数/额外抽象 | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/002-model-router/
├── spec.md
├── research.md
├── plan.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/qtua/aihub/
├── config/
│   └── GatewayProperties.java          # Deployment 增加 rpm/tpm；启动时校验权重同源
├── gateway/
│   ├── ChatCompletionService.java      # 注入 DeploymentSelector；记录选中部署
│   ├── ChatCompletionController.java   # 不变
│   ├── ModelController.java            # 新增 GET /models、/v1/models
│   ├── ModelService.java               # 从配置构造模型目录
│   ├── ModelListResponse.java          # OpenAI 兼容列表响应
│   └── router/
│       ├── DeploymentSelector.java     # 策略接口
│       └── SmoothWeightedSelector.java # 确定性平滑加权轮询

src/test/java/com/qtua/aihub/
├── config/GatewayPropertiesTest.java   # 权重绑定 + 混用失败
├── gateway/
│   ├── ChatCompletionServiceTest.java  # 更新构造；验证按选择结果转发
│   ├── ModelControllerTest.java        # /models、/v1/models 契约
│   ├── ModelServiceTest.java           # 别名列表、不泄露上游信息
│   └── router/SmoothWeightedSelectorTest.java
└── ArchitectureTest.java               # 不变
```

**Structure Decision**: 沿用单模块分包；路由策略独立为 `gateway/router` 子包，避免 `config` 依赖 `gateway`。`ModelController` 与 `ChatCompletionController` 同级。

## Phase 0: Research 结论摘要

详见 `research.md`：

1. 采用 NGINX 式平滑加权轮询（SWRR），确定性、交错、无随机性。
2. 每别名一个 `RoundState`，`synchronized` 保护 `currentWeight`；状态用 `ConcurrentHashMap` 按别名缓存。
3. 权重解析：`rpm` → `tpm` → 1；值 ≤ 0 按 1 并告警。
4. 同源校验在 `GatewayProperties` 的 `@PostConstruct` 执行，启动失败。
5. `/models` 返回 `{"object":"list","data":[{"id","object","created","owned_by"}]}`。
6. 路由日志记录别名、上游模型名与 `api-base`，绝不记录 `api-key`。

## Phase 1: Design 产物

- `data-model.md`：配置模型、运行时状态、响应模型、校验规则。
- `contracts/openapi.yaml`：`GET /models` 与 `GET /v1/models` 契约（聊天契约见 001）。
- `quickstart.md`：本地双部署配置、轮询验证、`/models` 验证、质量门禁。

## Complexity Tracking

> 无 Constitution 违规，无需豁免。
