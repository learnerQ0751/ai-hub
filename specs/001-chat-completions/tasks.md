# Tasks: Chat Completions Gateway (需求 1.1)

**Input**: `specs/001-chat-completions/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml

**Tests**: 本功能强制 TDD（Constitution II）；每个实现任务先写失败测试。

## Phase 1: Setup（已完成）

- [x] T001 创建 Spring Boot 骨架（pom.xml / AiHubApplication / application.yml）
- [x] T002 初始化规范库、生成器、硬门禁（`standards/`、`config/`、`-Pquality`）
- [x] T003 初始化 spec-kit / OpenSpec / git-ai
- [x] T004 首次提交 `00d0a9b`

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 配置绑定、错误体、请求追踪基础。

- [ ] T005 写 `GatewayPropertiesTest`：绑定 `gateway.model-list` 为 `Map<String, List<Deployment>>`，断言别名与部署字段
  - Test: `src/test/java/com/qtua/aihub/config/GatewayPropertiesTest.java`
  - 运行：`mvn -q -Dtest=GatewayPropertiesTest test`（预期失败：类不存在）
- [ ] T006 实现 `GatewayProperties` + `Deployment`
  - Create: `src/main/java/com/qtua/aihub/config/GatewayProperties.java`
  - 运行：`mvn -q -Dtest=GatewayPropertiesTest test`（预期 PASS）
- [ ] T007 写 `ErrorResponseTest`：JSON 结构包含 `error.message/type/code`
  - Test: `src/test/java/com/qtua/aihub/gateway/ErrorResponseTest.java`
- [ ] T008 实现 `ErrorResponse` + `ChatCompletionException` 体系
  - Create: `src/main/java/com/qtua/aihub/gateway/ErrorResponse.java`
  - Create: `src/main/java/com/qtua/aihub/gateway/InvalidModelException.java`
  - Create: `src/main/java/com/qtua/aihub/gateway/UpstreamException.java`
- [ ] T009 实现 `RequestIdFilter`（MDC + `X-Request-Id`）
  - Create: `src/main/java/com/qtua/aihub/filter/RequestIdFilter.java`

**Checkpoint**: 配置、错误体、请求 ID 就绪。

---

## Phase 3: User Story 1 - 统一入口与单模型转发 (P1) 🎯 MVP

**Goal**: `/v1/chat/completions` 与 `/chat/completions` 返回 OpenAI 结构，字段不丢。

**Independent Test**: curl 调用返回 200 + `object=chat.completion` + `usage`。

### Tests

- [ ] T010 [US1] 写 `ChatCompletionControllerTest`：200 场景，断言 `object/usage.total_tokens`
  - Test: `src/test/java/com/qtua/aihub/gateway/ChatCompletionControllerTest.java`
  - 运行：`mvn -q -Dtest=ChatCompletionControllerTest test`（预期失败）
- [ ] T011 [US1] 写 `UpstreamClientTest`：MockWebServer 验证路径 `/chat/completions`、Authorization、响应原样返回
  - Test: `src/test/java/com/qtua/aihub/gateway/UpstreamClientTest.java`

### Implementation

- [ ] T012 [US1] 实现 `UpstreamClient`
  - Create: `src/main/java/com/qtua/aihub/gateway/UpstreamClient.java`
  - 依赖：T006（GatewayProperties）
- [ ] T013 [US1] 实现 `ChatCompletionService`
  - Create: `src/main/java/com/qtua/aihub/gateway/ChatCompletionService.java`
  - 依赖：T012
- [ ] T014 [US1] 实现 `ChatCompletionController`
  - Create: `src/main/java/com/qtua/aihub/gateway/ChatCompletionController.java`
  - 依赖：T013
- [ ] T015 [US1] 配置 `application.yml` / `application-local.yml.example`
  - Modify: `src/main/resources/application.yml`
- [ ] T016 [US1] 运行单测与硬门禁
  - `mvn -q -Dtest=ChatCompletionControllerTest,UpstreamClientTest test`
  - `mvn -Pquality verify`

**Checkpoint**: US1 可独立演示。

---

## Phase 4: User Story 2 - 规范错误体 (P2)

- [ ] T017 [US2] 写错误场景测试：未知模型 400、messages 空 400、stream=true 400、上游 401/超时
  - Modify: `src/test/java/com/qtua/aihub/gateway/ChatCompletionControllerTest.java`
- [ ] T018 [US2] 实现全局异常处理 `@RestControllerAdvice`
  - Create: `src/main/java/com/qtua/aihub/gateway/GatewayExceptionHandler.java`
- [ ] T019 [US2] 实现上游异常映射（401/超时/其他）
  - Modify: `src/main/java/com/qtua/aihub/gateway/UpstreamClient.java`
  - Modify: `src/main/java/com/qtua/aihub/gateway/ChatCompletionService.java`
- [ ] T020 [US2] 运行错误场景测试与硬门禁
  - `mvn -Pquality verify`

**Checkpoint**: 四类错误均可复现。

---

## Phase 5: User Story 3 - 请求追踪 (P3)

- [ ] T021 [US3] 写 `RequestIdFilterTest`：响应头存在 `X-Request-Id`
- [ ] T022 [US3] 验证日志包含 request_id（启动后 curl + 查日志）
- [ ] T023 [US3] 运行硬门禁
  - `mvn -Pquality verify`

---

## Phase 6: Polish & Validation

- [ ] T024 启动应用并 curl 验收（对照 LiteLLM 基线）
  - `mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments="..."`
  - `curl -s http://127.0.0.1:18080/v1/chat/completions ...`
- [ ] T025 记录验收结果到任务目录 `13-需求1.1验收记录.md`
- [ ] T026 git-ai 归因报告（`git-ai stats --json`）
- [ ] T027 更新任务文件与复盘

## Dependencies & Execution Order

- Phase 2 阻塞 Phase 3-5。
- US1（T010-T016）是 MVP，必须最先完成。
- US2、US3 可在 US1 后并行（不同文件）。
- Phase 6 依赖所有用户故事完成。

## Notes

- 每完成一个任务运行对应测试；不得跳过失败测试。
- 提交前必须 `mvn -Pquality verify`。
- 本期明确不做：多部署、虚拟 Key、限流、fallback、流式。
