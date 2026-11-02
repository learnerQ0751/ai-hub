# REQ-1.4：限流与预算（Redis 实现）

- 日期：2026-11-02
- 状态：已完成
- 关联规格：无（单需求，按 REQ + TDD 执行）
- 依赖需求：REQ-1.3、REQ-1.5
- 需求来源：Vault `05-项目7-第1期-LLM网关核心.md` §REQ-1.4
- 接口：无新增 HTTP 接口；chat 新增 429/400 限流与预算错误；`/key/generate`、`/key/info` 新增 `budget_duration`
- 提交：0257305（初版）+ 1d1154c（网关治理解耦重构）+ b4b8442（Filter 统一治理）+ 7b798a7（删除 ChatExecutor 假出口）

## 1. 是什么

给虚拟 Key 增加两类治理能力：
1. **限流**：Key 级 RPM（每分钟请求数）和 TPM（每分钟 Token 数），按 `key + model` 维度用 Redis 滑动窗口计数，超限返回 429；
2. **预算**：Key 级 `max_budget` + `budget_duration`（daily/monthly），按 REQ-1.5 的价格表累计成本，超预算拒绝。

多实例共享同一 Redis，计数一致；Redis 不可用时默认 fail-open 并告警。

## 2. 有什么用

- 业务价值：防止单个 Key 打爆上游或预算失控；为成本看板（REQ-4.3）与审计（REQ-4.5）提供限流/预算命中数据。
- 对应参照行为：B-10（LiteLLM 超限状态码与错误体，待补测）；B-08 `/key/info` 的 spend 与预算字段。
- 面试叙事点：滑动窗口与原子 Lua、Key+model 维度、预算周期、fail-open 取舍、多实例一致性。

## 3. 怎么实现的

- 分层落点：`start/filter`（GatewayGovernanceFilter，统一治理入口）→ `service/gateway`（GatewayGovernanceService，限流/预算/计价/日志公共实现）→ `service`（ChatCompletionServiceImpl，业务解析/执行）→ `core`（纯业务：路由/上游/usage）→ `integration`（Redis Lua）→ `api/manager/dal`（Key 字段）。
- 核心类：`GatewayGovernanceFilter`、`GatewayGovernanceService`、`DefaultGatewayGuard`、`RedisRateLimiter`、`ChatCompletionCore`。
- 请求链路：

```text
AuthFilter（KeyAuthDTO 携带 rpm/tpm/maxBudget/budgetDuration）
→ GatewayGovernanceFilter（pre：RPM → TPM 预占 → 预算预占；超限 429/400）
→ Controller → ChatCompletionServiceImpl → ChatCompletionCore（选部署/上游/usage）
→ Controller 回填 usage/deploymentModel → Filter（post：计价、结算、spend、call_logs）
→ 失败：ExceptionHandler 回填 status/errorType → Filter release + call_logs
```

## 4. 影响面

```yaml
capability:
  type: cross-cutting
  entry_point: Filter + Service
  interface_agnostic: true
modules:
  add: [integration 限流实现]
  change: [common, api, manager, dal, core, service, start, tests]
interfaces:
  add: []
  change:
    - "POST /v1/chat/completions（新增 429/400 限流与预算错误）"
    - "POST /chat/completions（同上）"
    - "POST /key/generate（新增 budgetDuration）"
    - "GET /key/info（新增 budgetDuration）"
  remove: []
tables:
  add: []
  change:
    - api_keys（新增 budget_duration）
    - call_logs（新增 error_type）
config: [gateway.rate-limit]
depends_on: [REQ-1.3, REQ-1.5]
compat: additive
gate: auto
regression: [REQ-1.1 聊天链路, REQ-1.2 路由, REQ-1.3 Key 管理, REQ-1.5 计量]
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| Key 级 rpm/tpm 按 `key + model` 维度计数 | 需求卡明确；不同模型互不影响；后续可扩展 Key 总额度维度 | 2026-11-02 |
| 滑动窗口用 Redis ZSET + Lua（窗口 60s） | 精确、原子、多实例一致；ZSET 支持按时间清理 | 2026-11-02 |
| tpm 请求前按「输入估算 + max_tokens 上限」预占，响应后按实际 usage 结算 | 请求前不知道输出 token；预占保证不超限，结算保证统计与 1.5 一致 | 2026-11-02 |
| 预算请求前预占估算成本、响应后按实际 cost 结算 | 避免并发超支；失败释放；周期 key 带 TTL | 2026-11-02 |
| 预算金额用整数 micro 单位（1e-8 元）存 Redis | 避免 INCRBYFLOAT 的 double 精度问题 | 2026-11-02 |
| 预算周期：daily=`yyyyMMdd`、monthly=`yyyyMM`，key 带 TTL | 跨期自动切换；无需定时任务 | 2026-11-02 |
| 超限：rpm/tpm 返回 429 `rate_limit_error`；预算返回 400 `budget_exceeded` | 对齐 LiteLLM 语义；B-10 未实测，待补测校准 | 2026-11-02 |
| Redis 故障默认 fail-open + WARN，可配置 | 可用性优先；限流是保护而非业务正确性 | 2026-11-02 |
| 限流/预算命中写 call_logs 的 `error_type` | 供 REQ-4.x 统计；不新增独立日志表 | 2026-11-02 |
| 限流/预算/计价/spend/访问日志放 `GatewayGovernanceFilter + GatewayGovernanceService`，业务 Service/Core 不感知 | 横切治理必须统一入口、接口无关；新增流式/文件接口自动复用，不复制治理逻辑 | 2026-11-02 |
| 未来物理拆分用 HTTP 契约（OpenAI 或内部 ChatCommand），当前 Service 直接调 Core | `ChatExecutor` 绑定内部 BO/含部署密钥，是假出口；YAGNI 删除 | 2026-11-02 |

## 6. 执行摘要

### 2026-11-02

- 进展：完成 Key 字段扩展（`budget_duration`）、`call_logs.error_type`、`RedisRateLimiter`（RPM/TPM ZSET 滑动窗口、预算预占-结算、micro 单位、fail-open/closed）、core 编排与真实链路验证。
- 测试：新增 `RedisRateLimiterTest` 13 个、`RateLimitIntegrationTest` 6 个（Testcontainers Redis：RPM 429、TPM 结算、预算耗尽/周期切换、并发一致、窗口恢复）；全量门禁通过。
- 真实链路：Flyway V3 迁移成功；`rpm_limit=2` 前两次 200、第三次 429 `rate_limit_error`；`max_budget=0.0001` 返回 400 `budget_exceeded`；`tpm_limit=100` 返回 429；`/key/info` 返回 `budget_duration`；`call_logs` 记录 status/error_type。
- 下一步：提交并进入 REQ-1.6（重试/fallback/冷却）。

### 2026-11-02（网关治理解耦重构）

- 背景：评审指出限流/预算/计量放在 `ChatCompletionCore` 属于职责错位，且初版 `GatewayChatCompletionService` 仍把治理绑定 chat 接口；裁决不引入 Dubbo/Nacos，按 HTTP + 统一 Filter 治理。
- 重构：新增 `GatewayGovernanceFilter`（统一入口）+ `service/gateway/GatewayGovernanceService`（公共治理）+ `GatewayContext`；`ChatCompletionServiceImpl` 只做解析/校验/执行；`ChatCompletionCore` 只保留部署选择/协议/上游/usage；计价、spend、日志、限流、预算全部上移；ArchUnit 允许 Service → Integration。
- 验证：`mvn -Pquality verify` 全绿（152 测试）；CallLog/RateLimit/GatewayRouting/KeyManagement 集成测试通过；真实链路 rpm=2 第三次 429、预算 400、日志 error_type 正常。
- 设计文档：Vault `15-网关治理解耦设计`。
- 下一步：提交 → REQ-1.6。

## 7. 复盘

- 做对了：TPM/预算用"预占-结算"解决输出 token 未知；micro 单位避免浮点误差；周期 key + TTL 免定时任务；fail-open 保证 Redis 故障时网关可用。
- 踩了什么坑：Spring 多构造器需要 `@Autowired` 指定注入构造器；Testcontainers 在 Docker Hub 不可达时需禁用 Ryuk 并使用本地镜像；Windows 下被占用的 exec jar 会阻止 clean，验证时用 `-Dspring-boot.repackage.skip=true`。
- 下次改进：流式（REQ-1.7）的 TPM 结算需放在异步完成回调（Filter 的 `onCompletion`）；请求体缓存只适用于小 JSON，大文件接口改用流式提取；预算预占的幂等/过期清理生产化时补齐；未来拆网关应用时用 HTTP 契约（OpenAI 请求/响应或内部 ChatCommand）替换本地 Core 调用。

## 8. 多实例检查

- [x] 是否有可变成员变量？`RedisRateLimiter`/`GatewayChatCompletionService` 无请求级可变状态；限流与预算全部在 Redis
- [x] 计数器/限流/预算/冷却是否在 Redis/数据库？RPM/TPM/预算在 Redis；Key 配置在 PostgreSQL
- [x] 锁是否分布式？无应用层锁；Lua 脚本原子执行，多实例共享同一计数器
- [x] 定时任务是否只执行一次？无定时任务；预算周期靠 key 后缀 + TTL 切换
- [x] 本地缓存是否可容忍过期/不一致？无本地业务缓存
