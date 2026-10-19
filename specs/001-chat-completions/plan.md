# Implementation Plan: Chat Completions Gateway (需求 1.1)

**Branch**: `001-chat-completions` | **Date**: 2026-10-19 | **Spec**: `specs/001-chat-completions/spec.md`

**Input**: Feature specification from `specs/001-chat-completions/spec.md`

## Summary

实现 OpenAI 兼容的 `/v1/chat/completions` 与 `/chat/completions`：按模型别名从 YAML 配置解析上游部署，使用 Spring `RestClient` 转发到 DeepSeek，原样透传响应；统一错误体；生成 `request_id` 并记录日志。本期只做单部署、非流式，拒绝 `stream=true`。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.11（Web、Validation）、Jackson、Spring RestClient

**Storage**: 无（本期不引入数据库；配置来自 YAML）

**Testing**: JUnit 5、Spring MockMvc、MockWebServer、ArchUnit、JaCoCo

**Target Platform**: Windows 11 本地开发；后续 Linux 部署

**Project Type**: Web service（单模块 Maven 项目）

**Performance Goals**: 本期不设硬指标；转发延迟主要取决于上游

**Constraints**: 需代理 127.0.0.1:7897；密钥不入库；硬门禁 `mvn -Pquality verify` 必须通过

**Scale/Scope**: 单机、单模型别名、单部署；7 个需求中的第 1 个

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结果 |
|---|---|---|
| I. Security First | 密钥来自环境/本地配置；错误体不泄露上游 key | PASS |
| II. Test-First & Verification | Controller/UpstreamClient 先写失败测试；硬门禁 | PASS |
| III. Layered Boundaries | Controller → Service → UpstreamClient；config 独立 | PASS |
| IV. OpenAI Compatibility & Observability | 原样透传；规范错误体；request_id 日志 | PASS |
| V. Simplicity & Maintainability | 单模块、无数据库、无过度抽象 | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/001-chat-completions/
├── spec.md
├── research.md
├── plan.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/qtua/aihub/
├── AiHubApplication.java
├── config/
│   ├── GatewayProperties.java
│   └── RestClientConfig.java
├── gateway/
│   ├── ChatCompletionController.java
│   ├── ChatCompletionService.java
│   ├── UpstreamClient.java
│   ├── ChatCompletionException.java
│   ├── InvalidModelException.java
│   ├── UpstreamException.java
│   └── ErrorResponse.java
└── filter/
    └── RequestIdFilter.java

src/test/java/com/qtua/aihub/
├── ArchitectureTest.java
└── gateway/
    ├── ChatCompletionControllerTest.java
    └── UpstreamClientTest.java
```

**Structure Decision**: 单模块 Maven 项目；按职责分包 `config` / `gateway` / `filter`；测试按被测类拆分。

## Complexity Tracking

> 无 Constitution 违规，无需豁免。
