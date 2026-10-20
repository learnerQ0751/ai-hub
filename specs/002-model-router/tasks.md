---

description: "Task list for Model Configuration & Multi-Deployment Routing"
---

# Tasks: Model Configuration & Multi-Deployment Routing (需求 1.2)

**Input**: Design documents from `specs/002-model-router/`

**Prerequisites**: `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/openapi.yaml`

**Tests**: 项目宪法要求 TDD，所有实现任务前必须有对应失败测试。

**Organization**: 按用户故事分阶段；US1/US2 为 P1，US3 为 P2，US4 为 P3。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事
- 所有路径为仓库根目录相对路径

## Phase 1: Setup

**Purpose**: 确认现有构建与配置基线（项目已存在，仅做增量准备）

- [x] T001 确认 `mvn -Pquality verify` 在当前分支通过，记录基线结果

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 配置模型与路由抽象，US1–US4 均依赖

**CRITICAL**: 本阶段完成前不得开始用户故事实现

- [x] T002 在 `src/main/java/com/qtua/aihub/config/GatewayProperties.java` 的 `Deployment` 中增加可空 `Integer rpm`、`Integer tpm` 字段及 getter/setter
- [x] T003 [P] 在 `src/test/java/com/qtua/aihub/config/GatewayPropertiesTest.java` 增加失败测试：`rpm`/`tpm` 绑定；同一别名全部 `rpm`、全部 `tpm`、全部缺失可启动；`rpm` 与 `tpm` 混用、部分配置导致 `ApplicationContextRunner` 启动失败
- [x] T004 在 `GatewayProperties` 中增加 `@PostConstruct` 校验：同一别名权重字段必须完全同源，否则抛 `IllegalStateException`；权重值 ≤ 0 记录 `WARN`（实现 T003 的测试）
- [x] T005 [P] 创建 `src/main/java/com/qtua/aihub/gateway/router/DeploymentSelector.java`：`Deployment select(String alias, List<Deployment> deployments)`

**Checkpoint**: 配置可绑定权重，错误配置启动即失败，策略接口就绪

---

## Phase 3: User Story 1 - 同名多部署加权轮询 (Priority: P1) MVP

**Goal**: 同一别名的多个部署按权重确定性交错命中

**Independent Test**: `chat` 双部署权重 1:1 / 3:1 时，100 次选择命中次数符合比例；单部署恒定命中

### Tests for User Story 1

> 先写测试并确认失败

- [x] T006 [P] [US1] 创建 `src/test/java/com/qtua/aihub/gateway/router/SmoothWeightedSelectorTest.java`：1:1 比例、3:1 比例、连续调用交错、单部署恒定、权重缺失按 1、权重 ≤ 0 按 1
- [x] T007 [P] [US1] 更新 `src/test/java/com/qtua/aihub/gateway/ChatCompletionServiceTest.java`：注入 `DeploymentSelector`；构造返回第二个部署时转发到第二个部署

### Implementation for User Story 1

- [x] T008 [US1] 创建 `src/main/java/com/qtua/aihub/gateway/router/SmoothWeightedSelector.java`：`ConcurrentHashMap` 按 `alias + "#" + size` 缓存 `RoundState`；`synchronized` 执行 SWRR；`long` 累计权重
- [x] T009 [US1] 修改 `src/main/java/com/qtua/aihub/gateway/ChatCompletionService.java`：注入 `DeploymentSelector`，用选择结果替换 `deployments.get(0)`；转发前记录 `INFO` 日志（别名、上游模型名、`api-base`，不含 `api-key`）

**Checkpoint**: 多部署轮询可独立验证；单部署行为不变

---

## Phase 4: User Story 2 - 配置驱动的模型别名 (Priority: P1)

**Goal**: 仅改 YAML 即可新增/调整别名与权重；异常配置有明确行为

**Independent Test**: 新增别名后重启即可请求；空部署列表返回规范错误体；单部署与 1.1 一致

- [x] T010 [P] [US2] 在 `src/test/java/com/qtua/aihub/gateway/ChatCompletionServiceTest.java` 增加测试：别名部署列表为空时返回 `invalid_request_error`；连续 20 次单部署均命中同一部署
- [x] T011 [US2] 更新 `src/main/resources/application-local.yml.example`：`chat` 双部署（`rpm` 3:1）+ `reasoner` 单部署
- [x] T012 [US2] 实现 T010 所需的空列表防御逻辑调整（保持 `ChatCompletionException.invalidModel` 契约）

**Checkpoint**: 配置变更无需改代码；异常配置行为可验证

---

## Phase 5: User Story 3 - 模型目录查询 (Priority: P2)

**Goal**: `GET /models` 与 `/v1/models` 返回 OpenAI 兼容别名列表，不泄露部署信息

**Independent Test**: 配置 `chat`/`reasoner` 后调用接口，仅返回两个别名及固定元数据

### Tests for User Story 3

- [x] T013 [P] [US3] 创建 `src/test/java/com/qtua/aihub/gateway/ModelServiceTest.java`：只含别名、保持配置顺序、不包含上游模型名/api-base/api-key/rpm/tpm
- [x] T014 [P] [US3] 创建 `src/test/java/com/qtua/aihub/gateway/ModelControllerTest.java`：`GET /models` 与 `/v1/models` 返回 200 及 OpenAI 兼容结构（按 `contracts/openapi.yaml`）

### Implementation for User Story 3

- [x] T015 [US3] 创建 `src/main/java/com/qtua/aihub/gateway/ModelListResponse.java`：嵌套 `ModelInfo(id, object, created, owned_by)` record
- [x] T016 [US3] 创建 `src/main/java/com/qtua/aihub/gateway/ModelService.java`：从 `GatewayProperties.getModelList()` 的 key 构造响应，`created=0`、`owned_by=ai-hub`
- [x] T017 [US3] 创建 `src/main/java/com/qtua/aihub/gateway/ModelController.java`：`@GetMapping({"/models", "/v1/models"})`

**Checkpoint**: 目录接口可独立验证，且不泄露上游信息

---

## Phase 6: User Story 4 - 可扩展的路由策略 (Priority: P3)

**Goal**: 请求处理仅依赖 `DeploymentSelector` 抽象，便于后续替换策略

**Independent Test**: `ChatCompletionService` 构造参数为接口；在测试中注入 mock 选择器即可改变路由结果

- [x] T018 [US4] 在 `ChatCompletionServiceTest` 中使用 mock `DeploymentSelector` 验证主链路与具体策略实现解耦（与 T007 合并实现）
- [x] T019 [US4] 运行 `ArchitectureTest`，确认新增 `gateway/router` 子包未引入包循环

**Checkpoint**: 新策略只需新增实现，无需改动 `ChatCompletionService`

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T020 更新 `ai-workflow/records/REQ-1.2.md`：决策、执行日志、验收勾选、复盘与理解验收
- [x] T021 运行 `mvn -Pquality verify`，确保 Checkstyle/SpotBugs/ArchUnit/JaCoCo 全绿
- [x] T022 按 `quickstart.md` 做本地验证：`/models`、轮询日志、单部署回归、混用配置启动失败
- [x] T023 运行 `ai-workflow/scripts/status.ps1` 更新 `ai-workflow/STATUS.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **Foundational (Phase 2)**: 依赖 T001；阻塞所有用户故事
- **US1 (Phase 3)**: 依赖 T004、T005
- **US2 (Phase 4)**: 依赖 US1（单部署路径）
- **US3 (Phase 5)**: 依赖 T002（只读配置）与 T001
- **US4 (Phase 6)**: 依赖 US1
- **Polish (Phase 7)**: 依赖所有故事

### User Story Dependencies

- **US1 (P1)**: Foundational 完成后可开始
- **US2 (P1)**: 依赖 US1 的 selector 单部署路径
- **US3 (P2)**: 可与 US1 并行（仅读配置），但需避免同时编辑 `GatewayProperties`
- **US4 (P3)**: 依赖 US1

### Within Each User Story

- 测试先写并失败 → 实现 → 测试通过
- 配置模型 → 选择器 → Service → 接口
- 完成后单独验证该故事

### Parallel Opportunities

- T003 与 T005 可并行（不同文件）
- T006 与 T007 可并行（不同测试文件）
- T013 与 T014 可并行（不同测试文件）
- US3 与 US1 可由不同人并行，但 `GatewayProperties` 的修改需先完成

---

## Parallel Example: User Story 1

```text
T006 编写 SmoothWeightedSelectorTest（失败）
T007 更新 ChatCompletionServiceTest 构造与选择断言（失败）
→ 两者完成后实现 T008、T009
```

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1、2
2. 完成 US1（T006–T009）
3. 验证双部署轮询与单部署回归
4. 再交付 US2（配置示例与异常路径）、US3（`/models`）、US4（接口解耦验证）

### Incremental Delivery

- US1：多部署轮询可用
- US2：配置示例与边界行为可用
- US3：模型目录可用
- US4：扩展点确认
- 每步后运行相关测试，最终统一跑质量门禁

---

## Notes

- 测试必须先在失败状态下确认，再实现
- `api-key` 不得出现在响应或日志
- 单部署路径必须保持需求 1.1 的响应契约
- 配置错误必须在启动期暴露，不进入运行时
