# 设计基线（ai-hub）

- 更新于：2026-11-02
- 对应状态：`2c0436a` + REQ-1.4 工作区（待提交）
- 维护规则：只有架构、运行时链路、跨需求决策发生变化时才更新；需求细节见 `records/`。

## 1. 系统全景

ai-hub 是 OpenAI 兼容的 LLM 网关（项目 7 · Agent 与 MCP 集群），行为对标 LiteLLM：

- 第 1 期：LLM 网关核心（统一入口 / 模型路由 / 虚拟 Key / 限流预算 / 日志计量 / 容错 / 流式）
- 第 2 期：MCP 与 Prompt 中心
- 第 3 期：RAG 与记忆服务
- 第 4 期：治理收尾（多租户 / Trace / 成本 / 管理端 / 审计）

架构图（唯一活图）：`acceptance/assets/design.svg`（源文件 `design.drawio` 同目录）。

```text
客户端 ──Bearer──> AuthFilter ──> Controller ──> Service ──> Core ──> Integration ──> 上游 LLM
                     │                                        │
                     └─ api_keys（虚拟 Key / 白名单）           └─ 路由选择 / 协议改写
```

## 2. 模块与依赖

| 模块 | 职责 |
|---|---|
| `ai-hub-common` | 异常、常量、配置模型（GatewayProperties）、模型目录端口（ModelCatalog/ModelDeployment）、工具 |
| `ai-hub-api` | Service 接口 + Req/Res DTO（OpenAI 契约） |
| `ai-hub-manager` | BO 与 BO↔DO 转换（ChatCompletionBO / ApiKeyBO）；模型目录 YAML 实现（YamlModelCatalog） |
| `ai-hub-dal` | MyBatis Mapper + DDL（api_keys） |
| `ai-hub-integration` | 上游 HTTP + OpenAI 协议适配 + 协议改写 |
| `ai-hub-core` | 业务编排：路由选择、模型白名单、别名改写决策 |
| `ai-hub-service` | ServiceImpl：校验 + DTO↔BO + 编排 core |
| `ai-hub-start` | 启动类（根包）+ controller / filter / error / config |
| `ai-hub-tests` | 集成测试 + ArchUnit + JaCoCo 聚合 |

依赖方向：`start → service → core → manager/integration → dal → common`（ArchUnit 强制，禁止反向）。

## 2.5 数据模型

- 表速查（按业务域、用途、关键关系、状态）见 `docs/DATA-MODEL.md`；`STATUS.md` 自动生成同名区块并与 DDL 校验（只显示当前已建表）。
- DDL 由 Flyway 管理：`ai-hub-dal/src/main/resources/db/migration/Vn__*.sql`，应用启动自动迁移（Spring Boot 4 需 `spring-boot-starter-flyway`）；测试 H2 仍用 `spring.sql.init`（Flyway 测试禁用）。
- 本地依赖容器：`postgres:16`（5432，db/user/pass=aihub）+ `redis:7.2`（6379）；Docker Hub 不可达时使用本地已有镜像。
- 当前已实现：`api_keys`（REQ-1.3）、`call_logs`（REQ-1.5）；规划：`mcp_servers`、`prompt_templates`、`rag_documents`、`rag_chunks`、`memories`、`audit_logs`。
- ER 图：表数量 ≥5 时补充 `acceptance/assets/data-model.drawio|svg`。

## 3. 核心运行时链路

### 3.1 聊天补全（`POST /v1/chat/completions`、`POST /chat/completions`）

```text
1. AuthFilter：Bearer token 认证
   - == master key → KeyAuthDTO.master()
   - 否则 SHA-256 查 api_keys：不存在/封禁/过期 → 401 auth_error
   - /key/* 仅 master
2. ChatCompletionController：原始 JSON → ChatCompletionReqDTO（携带 KeyAuthDTO）
3. ChatCompletionServiceImpl：校验 model/stream/messages；按别名取部署；组装 BO
4. ChatCompletionCore：
   ① 白名单校验（非 master 且白名单非空且不含 alias → 403 key_model_access_denied）
   ② 部署为空 → 400 invalid_model
   ③ Key 级限流/预算：RPM 滑动窗口 → TPM 预占 → 预算预占（超限 429/400）
   ④ 加权随机选部署 → model 别名改写为上游真实名 → 调上游 → 响应 model 回写别名
   ⑤ 解析 usage → 按上游真实模型查价格 → BigDecimal 计价
   ⑥ 按实际用量结算 TPM/预算 → 同步累加 api_keys.spend → 异步写 call_logs（成功/失败都写）
5. 返回上游原始 JSON（usage 等原样透传）
```

### 3.4 限流与预算（REQ-1.4）

```text
AuthFilter（KeyAuthDTO 携带 rpm_limit / tpm_limit / max_budget / budget_duration）
→ Core：
   ① RPM：Redis ZSET 滑动窗口（key + model，窗口 60s）
   ② TPM：输入估算（字符/4）+ max_tokens 预占，响应后按实际 usage 结算
   ③ 预算：估算成本 × 预占，响应后按实际 cost 结算；失败释放
   ④ 超限：rpm/tpm → 429 rate_limit_error；预算 → 400 budget_exceeded
   ⑤ Redis 故障：默认 fail-open（WARN），可配置 fail-closed
   ⑥ 命中写 call_logs.error_type，供 REQ-4.x 统计
```

### 3.5 计量与日志（REQ-1.5）

```text
RequestIdFilter（生成 request_id）→ Controller（请求属性）→ Service → Core
→ 上游响应 usage（prompt/completion/total）
→ YamlPricingCatalog（每 1M tokens 单价，缺失 WARN + cost=0）
→ ModelPrice.costOf（BigDecimal scale=8）
→ CallLogManager.addSpend（原子 UPDATE，虚拟 Key）
→ CallLogManager.record（有界线程池异步 INSERT；饱和时 CallerRuns；失败 ERROR 兜底）
```

### 3.2 Key 管理（仅 master）

```text
POST /key/generate → SecureRandom 生成 sk- 明文 → SHA-256 → INSERT api_keys → 返回明文（仅一次）
GET  /key/info     → SHA-256 查库 → 掩码 key + spend/models/expires/...
POST /key/block    → UPDATE blocked=true → 后续鉴权 401
POST /key/unblock  → UPDATE blocked=false → 恢复
```

### 3.3 模型目录

```text
GET /models、GET /v1/models → 只返回别名（不泄露上游模型名/api-base/api-key）
```

## 4. 跨需求设计决策

| 决策 | 理由 | 引入需求 | 日期 |
|---|---|---|---|
| 请求 model 转发前改写为部署真实模型；响应 model 回写别名 | 上游只认真实模型名；对外保持别名语义 | REQ-1.1 | 2026-10-19 |
| 无状态加权随机路由（WeightedRandomSelector） | 多实例部署天然安全；有状态轮询需 Redis | REQ-1.2 | 2026-10-20 |
| 权重 rpm 优先 → tpm 回退 → 默认 1；同一别名必须同源 | 避免量级混用导致路由偏斜 | REQ-1.2 | 2026-10-20 |
| 虚拟 Key 只存 SHA-256，明文仅生成时返回一次 | 降低泄露风险；高熵无需加盐 | REQ-1.3 | 2026-10-22 |
| 虚拟 Key 白名单校验在 core，先授权 403 后未知模型 400 | 业务规则归属；避免状态码探测模型目录 | REQ-1.3 | 2026-10-22 |
| `/key/*` 管理面仅 master；虚拟 Key 只走数据面 | 管理面与数据面隔离 | REQ-1.3 | 2026-10-22 |
| 持久层统一 MyBatis（禁止 JdbcClient/JPA） | 对齐公司规范 | REQ-1.3 | 2026-10-22 |
| 网关转发保持原始 JSON，不经过 LangChain4j 归一化 | 避免字段丢失；AI 框架用于 2 期起的 MCP/RAG | 架构基线 | 2026-10-20 |
| 模型目录收口为 `ModelCatalog` 端口（YAML 实现） | 业务层不依赖配置类；未来迁 DB + 缓存只换实现 | 架构重构 | 2026-10-30 |
| rpm/tpm 分层：部署 `weight` / Key 级限流 | 避免路由权重与限流阈值同名混用 | 团队决策 | 2026-10-30 |
| 容错 = 别名级 fallbacks + 部署 priority 主备 | 高可用需要两层配合：先主备、后降级 | 团队决策 | 2026-10-30 |
| 上游 api-key AES-256-GCM + 环境密钥 | 金融场景密钥不得明文入库；KMS 留扩展 | 团队决策 | 2026-10-30 |
| prod 环境 master key 空配置启动失败 | 防止生产网关无鉴权裸奔 | 团队决策 | 2026-10-30 |
| 价格按上游真实模型匹配；1M tokens 单价 + BigDecimal scale=8 | 同一别名可路由不同上游模型，按真实模型才可复算；金额禁用 double | REQ-1.5 | 2026-11-02 |
| 日志异步、spend 同步原子累加 | 日志不阻塞响应；spend 需尽快供预算判断，`spend = spend + ?` 防丢更新 | REQ-1.5 | 2026-11-02 |
| 价格缺失只告警、cost=0，不阻断调用 | 对齐 LiteLLM B-04 教训：缺价格不能造成调用失败或错误成本 | REQ-1.5 | 2026-11-02 |
| 失败调用也写日志（status=HTTP 码），不计成本 | 审计与排障需要失败面；失败不计费，重试语义留待 REQ-1.6 | REQ-1.5 | 2026-11-02 |
| Key 级 rpm/tpm 按 `key + model` 维度、Redis ZSET 滑动窗口 | 需求卡指定；模型间互不影响；ZSET 支持精确时间窗口清理 | REQ-1.4 | 2026-11-02 |
| TPM 请求前按「输入估算 + max_tokens」预占、响应后按实际 usage 结算 | 输出 token 请求前未知；预占保证不超限，结算保证口径与 REQ-1.5 一致 | REQ-1.4 | 2026-11-02 |
| 预算金额用整数 micro 单位（1e-8 元），周期 key 带 TTL | 避免 INCRBYFLOAT 精度问题；daily/monthly 跨期自动换 key，无需定时任务 | REQ-1.4 | 2026-11-02 |
| 限流/预算 Redis 故障默认 fail-open，可配置 fail-closed | 限流是保护而非业务正确性；避免 Redis 故障导致网关整体不可用 | REQ-1.4 | 2026-11-02 |

## 5. 演进记录

| 日期 | 需求 | 能力变化 |
|---|---|---|
| 2026-10-19 | REQ-1.1 | 统一入口 + 单模型转发 + 错误体 + 请求 ID + master key 占位 |
| 2026-10-20 | REQ-1.2 | 模型配置 + 多部署加权路由 + `/models` |
| 2026-10-20 | 重构 | Maven 9 模块分层（对齐监管报送）+ Spring Boot 4.1.1 / LangChain4j 1.22.0 / Redis |
| 2026-10-22 | REQ-1.3 | 虚拟 Key（生成/哈希鉴权/白名单/封禁/查询） |
| 2026-11-02 | REQ-1.5 | 调用日志 + Token 计量 + 显式价格表 + spend 累计 + 失败留痕 |
| 2026-11-02 | REQ-1.4 | Key 级 RPM/TPM 滑动窗口 + 预算预占结算 + 限流命中留痕 |

## 6. 与 LiteLLM 的差异

| 项 | 相同 | 不同 + 理由 |
|---|---|---|
| 入口 | `/chat/completions`、`/v1/chat/completions` | 同 |
| 模型别名 | model_list 别名 → 部署列表 | 同；配置为 Spring YAML 绑定 |
| 路由 | simple-shuffle 加权随机 | 同；以无状态实现（多实例安全） |
| 虚拟 Key | `sk-` 前缀、models 白名单、block/unblock、info | 同；只存 SHA-256，info 只回显掩码 |
| 错误体 | OpenAI error 结构 | 同；错误码字段与类型对齐基线 |
| 计费 | spend 累计 | 已实现（REQ-1.5）；显式价格表按上游真实模型配置，缺失告警且 cost=0（避免 LiteLLM B-04 的 spend=0 静默问题） |
| 限流/预算 | Key 级 rpm/tpm、max_budget、budget_duration | 已实现（REQ-1.4）；滑动窗口 + 预占-结算；超限 429/400；Redis 故障默认 fail-open |
