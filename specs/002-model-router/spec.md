# Feature Specification: Model Configuration & Multi-Deployment Routing (需求 1.2)

**Feature Branch**: `002-model-router`

**Created**: 2026-10-20

**Status**: Draft

**Input**: 项目 7 第 1 期需求 1.2——模型配置 + 多部署路由，在需求 1.1 单模型转发之上增加同名多部署负载均衡与模型目录查询，对标 LiteLLM `model_list` / `simple-shuffle` 行为基线。

## Clarifications

### Session 2026-10-20

- Q: 多部署的权重应从哪个参数读取？ → A: `rpm` 优先、`tpm` 回退、默认 1；同一别名下所有部署必须同源（全 `rpm` 或全 `tpm`），混用启动报错。
- Q: 多部署选择是否必须确定性轮流？ → A: 确定性平滑加权轮询。

## User Scenarios & Testing

### User Story 1 - 同名多部署加权轮询 (Priority: P1)

调用方仍然只认一个模型别名；该别名下配置了多个上游部署时，网关按权重把连续请求分摊到不同部署，使单一部署的配额与故障风险被稀释。

**Why this priority**: 这是本需求的核心价值；没有它，多部署配置只是一份静态列表。

**Independent Test**: 为别名 `chat` 配置两个部署（权重 1:1），连续调用 N 次，两个部署均被使用且比例接近 1:1（通过网关日志观察所选部署）。

**Acceptance Scenarios**:

1. **Given** 别名 `chat` 下有两个权重相等的部署，**When** 连续调用 100 次，**Then** 两个部署都被选中，且各自命中次数与期望值偏差不超过 5%。
2. **Given** 两个部署权重为 3:1，**When** 连续调用 100 次，**Then** 两个部署命中次数比例约为 3:1（偏差不超过 5%）。
3. **Given** 别名 `reasoner` 只有一个部署，**When** 连续调用多次，**Then** 每次都使用该部署，行为与需求 1.1 一致。

---

### User Story 2 - 配置驱动的模型别名 (Priority: P1)

运维人员通过配置文件新增或修改模型别名、部署列表与权重，无需改动代码；服务重启后新配置生效。

**Why this priority**: 网关的模型接入能力必须可运营化，否则每接一个模型都要发版。

**Independent Test**: 仅修改 YAML，新增别名 `embed` 及其部署，重启后 `/models` 中出现 `embed` 且请求可正常转发。

**Acceptance Scenarios**:

1. **Given** 配置文件中新增别名及其部署，**When** 重启服务并调用 `/models`，**Then** 新别名出现在列表中。
2. **Given** 配置文件中调整某别名的部署顺序或权重，**When** 重启并连续调用，**Then** 路由行为按新配置生效。
3. **Given** 别名下部署列表为空，**When** 请求该别名，**Then** 返回规范错误体且服务不崩溃。

---

### User Story 3 - 模型目录查询 (Priority: P2)

调用方通过 `GET /models` 获取网关支持的模型别名列表，用于能力发现与客户端配置。

**Why this priority**: 没有目录接口，调用方只能靠文档猜测可用模型；但它不阻塞请求转发主链路。

**Independent Test**: 配置 `chat` / `reasoner` 两个别名后调用 `GET /models`，返回 OpenAI 兼容列表，`data[].id` 恰为两个别名。

**Acceptance Scenarios**:

1. **Given** 配置了 `chat` / `reasoner`，**When** `GET /models`，**Then** HTTP 200，列表包含且仅包含这两个别名。
2. **Given** 任意配置，**When** `GET /models`，**Then** 响应中不出现上游真实模型名、`api-base`、`api-key` 或权重参数。
3. **Given** 别名包含 `chat`，**When** `GET /v1/models`，**Then** 行为与 `GET /models` 一致。

---

### User Story 4 - 可扩展的路由策略 (Priority: P3)

后续需求要加入 least-busy / usage-based / latency-based 策略时，可以新增策略实现而不改动请求处理主链路。

**Why this priority**: 本期只交付加权轮询，但路由选择必须有稳定的扩展点，避免下次需求重写主链路。

**Independent Test**: 通过代码评审确认部署选择被抽象为可替换的组件，且请求处理只依赖该抽象。

**Acceptance Scenarios**:

1. **Given** 现有加权轮询实现，**When** 新增一个选择策略实现并配置启用，**Then** 请求处理主链路无需修改。

---

### Edge Cases

- 别名存在但部署列表为空 → 返回规范错误体（不泄露内部细节），服务不崩溃。
- 权重缺失 → 按默认权重 1 参与轮询。
- 权重为 0 或负数 → 视为无效配置，按 1 处理并在启动时记录告警日志。
- 同一别名下部分部署配置 `rpm`、部分配置 `tpm` → 视为配置错误，启动失败并给出明确错误信息。
- 同一别名下部分部署配置权重字段、其余未配置 → 同样视为配置错误，启动失败。
- 同一别名下全部未配置 `rpm`/`tpm` → 视为同源（默认 1），允许启动并按等权轮询。
- 所有部署权重相同 → 退化为均匀轮询。
- 未知模型别名 → 保持需求 1.1 的 400 `invalid_request_error` 行为。
- 配置在一次请求处理中保持不变；不做热加载，变更需重启。
- 轮询状态为进程内状态，重启后重新计数，不要求跨实例一致。
- `/models` 不探测上游可用性，只反映静态配置。
- 模型别名大小写敏感，按配置原样匹配。

## Requirements

### Functional Requirements

- **FR-001**: 系统 MUST 从配置读取「模型别名 → 部署列表」，每个部署包含 `api-base`、`api-key`、`model`，并可包含权重相关参数。
- **FR-002**: 系统 MUST 在同一别名的多个部署之间进行加权轮询选择；单部署时选择结果必须恒为该部署。
- **FR-003**: 系统 MUST 按配置的权重决定各部署被选中的比例：权重取 `rpm`；`rpm` 未配置时取 `tpm`；两者均未配置（或值 ≤ 0）时按 1 处理，并对无效值记录告警日志。同一别名下的部署权重字段 MUST 完全同源：全部配置 `rpm`、全部配置 `tpm`，或全部未配置；其他组合 MUST 导致启动失败并输出明确错误。
- **FR-004**: 系统 MUST 采用确定性平滑加权轮询，保证连续请求按权重交错分摊，偏差在统计上可观察且稳定（见 Success Criteria）。
- **FR-005**: 系统 MUST 将部署选择抽象为可替换的策略组件，请求处理主链路只依赖该抽象。
- **FR-006**: 系统 MUST 提供 `GET /models`（及 `/v1/models`），返回可用模型别名列表，采用 OpenAI 兼容列表结构。
- **FR-007**: `/models` 响应 MUST NOT 包含上游真实模型名、`api-base`、`api-key`、权重或其他部署参数。
- **FR-008**: 系统 MUST 保持需求 1.1 的请求/响应契约不变：响应 `model` 回写别名、上游字段原样透传、`usage` 不丢。
- **FR-009**: 系统 MUST 在网关日志中记录本次请求选中的部署（脱敏，不含 `api-key`），以便观察轮询。
- **FR-010**: 新增/修改别名、部署与权重 MUST 只需修改配置，无需修改代码。
- **FR-011**: 未知模型、空部署列表等错误 MUST 返回与需求 1.1 一致的规范错误体。
- **FR-012**: 系统 MUST 通过 `mvn -Pquality verify`（Checkstyle/SpotBugs/ArchUnit/JaCoCo）。

### Key Entities

- **ModelAlias**: 对外暴露的稳定模型名（如 `chat` / `reasoner`），是 `/models` 的唯一可见单元。
- **ModelDeployment**: 别名下的一个上游部署，含 `api-base`、`api-key`、`model` 及可选权重参数；仅内部可见。
- **DeploymentSelector**: 路由策略抽象；本期实现加权轮询，后续可替换。
- **ModelListResponse**: OpenAI 兼容的模型目录响应，仅含别名及对象元数据。

## Success Criteria

### Measurable Outcomes

- **SC-001**: 权重 1:1 的双部署，连续 100 次请求中每个部署命中次数与 50 的偏差不超过 5 次。
- **SC-002**: 权重 3:1 的双部署，连续 100 次请求中两个部署命中次数比例与 3:1 的偏差不超过 5 次。
- **SC-003**: 单部署别名连续 20 次请求 100% 命中同一部署，响应结构与需求 1.1 完全一致。
- **SC-004**: `/models` 输出只含配置的别名；抽查任意别名的响应体，不出现上游模型名、`api-base`、`api-key` 字段值。
- **SC-005**: 仅修改配置即可新增别名并在重启后生效，全程无代码变更。
- **SC-006**: `mvn -Pquality verify` 通过，单元测试覆盖配置绑定、权重轮询、未知模型、`/models` 输出。
- **SC-007**: 连续调用日志可明确看出所选部署，排障时无需抓包。

## Assumptions

- 本期只实现加权轮询（simple-shuffle 语义）；least-busy / usage-based / latency-based 在后续需求实现。
- 配置变更需要重启生效，不做热加载。
- 轮询状态仅保存在单实例进程内，不要求多实例间一致。
- `/models` 采用 OpenAI 兼容结构（`{"object":"list","data":[{"id":"<别名>","object":"model"}]}`），不探测上游可用性。
- 不做跨部署 fallback：选中的部署失败时按需求 1.1 的错误契约返回，fallback 在后续需求实现。
- 权重语义已确认（FR-003）：`rpm` 优先、`tpm` 回退、默认 1；同一别名内权重字段必须同源，混用视为配置错误并在启动时失败。仅配置 `tpm` 时按 tpm 原值加权。
- 轮询方式已确认（FR-004）：确定性平滑加权轮询，不引入随机性。
- 上游部署仍为 OpenAI 兼容端点；本机需代理 7897。
- 不引入数据库；配置来自 `application.yml` + `application-local.yml`（gitignored）。
