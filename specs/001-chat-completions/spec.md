# Feature Specification: Chat Completions Gateway (需求 1.1)

**Feature Branch**: `001-chat-completions`

**Created**: 2026-10-19

**Status**: Draft

**Input**: 项目 7 第 1 期需求 1.1——统一入口 + 单模型转发（非流式），对标 LiteLLM 行为基线。

## User Scenarios & Testing

### User Story 1 - 统一入口与单模型转发 (Priority: P1)

调用方使用 OpenAI 兼容请求访问网关，网关按模型别名转发到配置的上游（DeepSeek），并将上游响应原样返回。

**Why this priority**: 这是网关的最小可用闭环；没有它，后续路由、虚拟 Key、限流都无从谈起。

**Independent Test**: 启动应用后，用 curl 调用 `/v1/chat/completions`，返回包含 `id/object/model/choices/usage` 的 OpenAI ChatCompletion JSON。

**Acceptance Scenarios**:

1. **Given** 配置了别名 `chat-default` → DeepSeek `deepseek-chat`，**When** POST `/v1/chat/completions` 且 body 为 `{"model":"chat-default","messages":[{"role":"user","content":"hi"}]}`，**Then** 返回 HTTP 200，响应 `object=chat.completion`，`choices[0].message.content` 非空，`usage` 完整。
2. **Given** 同一配置，**When** POST `/chat/completions`（无 `/v1` 前缀），**Then** 行为与 `/v1/chat/completions` 一致。
3. **Given** 上游返回额外字段，**When** 网关透传，**Then** 响应字段不丢失（原样透传）。

---

### User Story 2 - 规范错误体 (Priority: P2)

当请求的模型不存在、上游鉴权失败、上游超时或请求体非法时，网关返回与 OpenAI/LiteLLM 对齐的错误体。

**Why this priority**: 错误契约是客户端重试与排障的基础；没有它，网关无法被稳定集成。

**Independent Test**: 调用不存在的模型，断言 HTTP 400 且 `error.type=invalid_request_error`；调用错误上游 key，断言 401/502 且错误体结构完整。

**Acceptance Scenarios**:

1. **Given** 请求 `model=no-such-model`，**When** 调用网关，**Then** HTTP 400，`{"error":{"message":"...","type":"invalid_request_error","param":"model","code":"400"}}`。
2. **Given** 上游返回 401，**When** 网关转发，**Then** 返回规范错误体（不泄露上游 key）。
3. **Given** 上游超时，**When** 网关转发，**Then** 返回 504/502 与规范错误体。

---

### User Story 3 - 请求追踪基础 (Priority: P3)

每个请求生成 `request_id`，写入响应头 `X-Request-Id` 与日志，便于排障和后续归因。

**Why this priority**: 为后续日志/计量/归因打基础；不阻塞主链路。

**Independent Test**: 调用任意接口，响应头包含 `X-Request-Id`，日志包含同一 ID。

**Acceptance Scenarios**:

1. **Given** 任意请求，**When** 网关处理，**Then** 响应头 `X-Request-Id` 存在且日志可检索到同一 ID。

---

### Edge Cases

- 请求 body 不是合法 JSON → 400 规范错误体。
- `model` 缺失或为空 → 400 规范错误体。
- `messages` 缺失或为空 → 400 规范错误体。
- 别名存在但部署列表为空 → 500/502 规范错误体。
- 上游连接失败/超时 → 502/504 规范错误体。
- 上游返回非 JSON → 502 规范错误体（不原样透传 HTML）。
- 请求带 `stream: true` → 本期明确返回 400「流式暂未实现」（需求 1.7 实现）。

## Requirements

### Functional Requirements

- **FR-001**: 系统 MUST 暴露 `POST /v1/chat/completions` 与 `POST /chat/completions`。
- **FR-002**: 系统 MUST 从配置读取模型别名 → 部署列表（`api-base` / `api-key` / `model`）。
- **FR-003**: 系统 MUST 按请求 `model` 别名选择配置的上游部署；本期只支持单部署。
- **FR-004**: 系统 MUST 将上游响应原样透传（JSON 字段不丢）。
- **FR-005**: 系统 MUST 返回 OpenAI ChatCompletion 兼容结构（至少 `id/object/model/choices/usage`）。
- **FR-006**: 系统 MUST 对未知模型返回 400 + `invalid_request_error`。
- **FR-007**: 系统 MUST 对上游错误/超时返回规范错误体，且不泄露上游密钥。
- **FR-008**: 系统 MUST 为每个请求生成 `request_id` 并写入响应头与日志。
- **FR-009**: 系统 MUST 校验 `messages` 非空；本期拒绝 `stream=true`。
- **FR-010**: 系统 MUST 通过 `mvn -Pquality verify`（Checkstyle/SpotBugs/ArchUnit/JaCoCo）。

### Key Entities

- **ModelAlias**: 用户侧模型名（如 `chat-default`）；对外暴露的稳定契约。
- **ModelDeployment**: 别名下的一个上游部署，含 `api-base`、`api-key`、`model`。
- **ChatCompletionRequest**: 原样转发的 JSON 请求体；本期只读取 `model` 与 `stream` 做校验。
- **ChatCompletionResponse**: 上游返回的 JSON 字符串；原样透传。
- **ErrorResponse**: `{"error":{"message","type","param","code"}}`。

## Success Criteria

### Measurable Outcomes

- **SC-001**: curl 调用 `/v1/chat/completions` 返回 200，且与 LiteLLM 基线逐字段对照通过（至少 `object/model/choices/usage`）。
- **SC-002**: 未知模型、上游 401、超时三类错误均返回规范错误体。
- **SC-003**: `mvn -Pquality verify` 通过；单测覆盖成功与关键异常场景。
- **SC-004**: 响应头 `X-Request-Id` 100% 出现，日志可按 ID 检索。

## Assumptions

- 本期只做单模型转发；多部署、负载均衡、虚拟 Key、限流、fallback、流式在后续需求实现。
- 上游使用 DeepSeek OpenAI 兼容端点；本机需代理 7897。
- 不引入数据库；配置来自 `application.yml` + `application-local.yml`（gitignored）。
- 鉴权本期只做 master key 占位（可选），虚拟 Key 在需求 1.3 实现。
