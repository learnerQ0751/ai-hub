# REQ-4.3：成本与用量统计

- 日期：2026-11-16
- 状态：已完成（待主人验收）
- 关联规格：`13-项目7-第4期-治理收尾.md` REQ-4.3
- 依赖需求：REQ-1.5、REQ-4.1
- 提交：c6ef262

## 1. 是什么

新增 `v_usage_daily` 实时聚合视图：按「天 / 租户 / 项目 / 模型别名 / Key」汇总请求数、
Token 与成本；`GET /v1/usage` 支持时间范围与维度过滤，返回汇总、明细与按天趋势；
`GET /v1/usage/export` 导出同口径 CSV；缺价上游模型单列在 `unpriced_models`。

## 2. 有什么用

- 业务价值：成本不可见就无法做预算与运营；管理端与租户都能看到「谁、哪天、用哪个模型花了多少」。
- 对应参照行为：LiteLLM Usage/Spend 页面；按 Key/Team/Model/日聚合。
- 面试叙事点：实时视图 vs 定时聚合的取舍；BigDecimal 精度；日期边界；虚拟 Key 的数据可见性；
  缺价模型为什么会 cost=0 以及如何告警。

## 3. 怎么实现的

- 分层：
  - V12 `v_usage_daily`：`GROUP BY CAST(created_at AS DATE), tenant_id, project_id,
    model_alias, key_id`，`SUM(prompt/completion/total_tokens/cost)`、`COUNT(*)`
  - `dal/mapper/UsageMapper` + XML：视图查询（动态过滤）、区间内上游模型查询
  - `manager/usage/UsageManager`：DO→BO
  - `service/UsageServiceImpl`：日期校验（默认近 7 天、最长 366 天）、租户/Key 收敛、
    汇总/趋势拼装、缺价模型（价格目录无该上游模型）、CSV
  - `start/controller/UsageController`：`GET /v1/usage`、`GET /v1/usage/export`
- 核心类：`UsageServiceImpl`、`UsageMapper.xml`、`V12__usage_view.sql`
- 链路（Mermaid）：

```mermaid
flowchart LR
    A["GET /v1/usage"] --> B["UsageServiceImpl：日期/范围校验"]
    B --> C["TenantScopeGuard：虚拟 Key 强制租户/项目/Key"]
    C --> D["UsageManager → v_usage_daily"]
    D --> E["汇总 + 明细 + 按天趋势"]
    E --> F["PricingCatalog：缺价上游模型 → unpriced_models"]
```

- 决策：**实时视图聚合**，不设定时任务。理由：口径与逐条日志天然一致、无重复聚合、
  无 leader/幂等负担；数据量增大后可平滑替换为物化视图/汇总表，接口不变。
- 精度：成本 `NUMERIC(18,8)`，服务内 `BigDecimal` 累加；不经过 double。
- 日期：`CAST(created_at AS DATE)` 按数据库会话时区；生产库统一 UTC。
- 可见性：master 可按任意租户/项目/模型/Key 过滤；虚拟 Key 强制 `tenant_id = 绑定租户`、
  `project_id = 绑定/授权项目`、`key_id = 自己`，请求其他租户 403。

## 4. 影响面

```yaml
capability:
  type: cross-cutting
  entry_point: "UsageController/UsageServiceImpl（读取侧，接口无关）"
  interface_agnostic: true
modules:
  add: [UsageController, UsageService/Impl, UsageManager, UsageMapper/XML, UsageAggregateDO/BO,
        UsageResDTO/Item/Trend/Totals]
  change: []
interfaces:
  add: ["GET /v1/usage", "GET /v1/usage/export"]
  change: []
tables:
  add: [v_usage_daily（视图）]
  change: []
config: []
depends_on: [REQ-1.5, REQ-4.1]
nfr:
  resources:
    - "只读视图查询，无新增连接池/线程/缓存"
  failure_modes:
    - "正常：视图按过滤条件聚合"
    - "无数据：返回 0 汇总与空明细"
    - "非法日期/超长区间：400"
    - "跨租户：403"
  metrics:
    - "usage 请求数与 cost 可与 call_logs 逐条复算"
compat: additive
gate: auto
regression:
  - "REQ-1.5 调用日志"
  - "REQ-4.1 租户隔离"
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 实时视图，不做定时汇总 | 与日志逐条可复算；避免重复聚合与时区/leader 问题 | 2026-11-16 |
| 日期闭区间 [from,to]，按 DB 日期截断 | 与运营口径一致；生产库 UTC | 2026-11-16 |
| 虚拟 Key 强制自己的 Key | 租户内也应最小可见，避免 Key 之间互相看用量 | 2026-11-16 |
| 缺价按价格目录判断而非 cost=0 | embedding/免费模型也可能 cost=0；必须区分「免费」与「缺价」 | 2026-11-16 |
| CSV 独立端点 | 避免 JSON/CSV 内容协商复杂化 | 2026-11-16 |

## 6. 执行摘要

- 2026-11-16：V12 视图、Usage API/CSV、缺价告警、租户/Key 收敛；`UsageIntegrationTest`
  覆盖按天聚合、租户隔离、跨租户 403、CSV 同口径。
- 提交：c6ef262。

## 7. 复盘

- 直接租用 call_logs 视图让「聚合数值与日志逐条复算」成为可验证的事实，而不是信任汇总任务。
- 先落 tenant/project 再聚合，避免租户过滤与统计口径分叉。
- 遗留：数据量增大后可将视图换成物化视图 + 定时刷新（接口不变）。

## 8. 多实例检查

- 纯读视图，无本地状态；多实例结果一致。
- 无定时任务，不存在 leader 选举问题。
