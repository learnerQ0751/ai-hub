# REQ-1.5：调用日志 + Token 计量 + `/key/info`

- 日期：2026-11-02
- 状态：进行中
- 关联规格：无（单需求，按 REQ + TDD 执行）
- 依赖需求：REQ-1.3
- 需求来源：Vault `05-项目7-第1期-LLM网关核心.md` §REQ-1.5
- 接口：无新增 HTTP 接口；`GET /key/info` 的 `spend` 由恒 0 改为真实累计
- 提交：待提交

## 1. 是什么

每次聊天补全调用都落一条调用日志：谁（虚拟 Key / master）在什么时候、调用哪个模型别名、路由到哪个上游模型、用了多少 Token、花了多少钱、耗时多少、成功还是失败。日志异步写入，不拖慢响应；虚拟 Key 的累计消费写回 `api_keys.spend`，`/key/info` 能查到真实花费。

## 2. 有什么用

- 业务价值：计量是限流、预算（REQ-1.4）、成本看板（REQ-4.3）、Trace（REQ-4.2）与审计（REQ-4.5）的数据底座；也是多租户治理的起点。
- 对应参照行为：B-03 `usage` 字段（prompt/completion/total）；B-04 LiteLLM 未匹配价格表导致 spend=0（本项目显式配置价格表，缺失时告警）；B-08 `/key/info` 聚合 spend。
- 面试叙事点：为什么日志异步但 spend 同步、价格表为什么必须显式配置、BigDecimal 计价与 1M token 单位换算、失败调用也要留痕。

## 3. 怎么实现的

- 分层落点：`common`（价格领域模型 + 端口 + TokenUsage 值对象）→ `manager`（YAML 价格目录、调用日志门面）→ `dal`（call_logs 表 / MyBatis）→ `core`（计量编排、失败留痕）→ `start`（request_id 透传）。
- 核心类：`CallLogManager`、`YamlPricingCatalog`、`ChatCompletionCore`。
- 请求链路：

```text
RequestIdFilter（生成 request_id）
→ Controller（读取 request_id 属性）→ Service → Core
→ 选部署 → 调上游 → 解析 usage → 按上游模型查价格 → BigDecimal 计价
→ 同步累加 api_keys.spend（虚拟 Key）→ 异步写 call_logs（成功/失败都写）
→ 返回响应（日志写入不阻塞）
```

## 4. 影响面

```yaml
modules:
  add: [manager 价格目录与调用日志门面, dal call_logs 数据访问]
  change: [common, core, service, start]
interfaces:
  add: []
  change: ["GET /key/info（spend 由恒 0 改为真实累计，字段不变）"]
  remove: []
tables:
  add: [call_logs]
  change: [api_keys（spend 原子累加；表结构不变）]
config: [gateway.pricing.models]
depends_on: [REQ-1.3]
compat: additive
gate: auto
regression: [REQ-1.1 聊天链路, REQ-1.3 /key/info 响应结构, REQ-1.2 /models]
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 价格按上游真实模型（`deployment_model`）匹配，而非别名 | 同一别名可路由到不同上游模型，按真实模型计价才可复算 | 2026-11-02 |
| 单价单位为「每 1M tokens」，`BigDecimal` scale=8、HALF_UP | 对齐价格表习惯；避免 double 精度误差；`api_keys.spend` 为 NUMERIC(18,8) | 2026-11-02 |
| 日志异步（有界队列 + CallerRunsPolicy），spend 同步原子 UPDATE | 日志不阻塞响应且拒绝策略保证不丢；spend 需尽快供预算判断 | 2026-11-02 |
| `call_logs.key_id` 存虚拟 Key 的 SHA-256；master 调用为 NULL | 当前 api_keys 无稳定对外 ID；哈希已是全链路 Key 身份；避免存明文 | 2026-11-02 |
| 价格缺失：启动 WARN + 调用照常成功 + cost=0 | 参照 B-04 的教训：缺价格不能让调用失败，也不能产生错误成本 | 2026-11-02 |
| 失败调用也落日志（status=HTTP 状态码） | 审计与排障需要失败面；只用成功样本会掩盖上游故障 | 2026-11-02 |
| 日志写入失败时以 ERROR 级结构化字段兜底 | 无法持久化时至少可从应用日志恢复关键字段 | 2026-11-02 |

## 6. 执行摘要

### 2026-11-02

- 进展：需求记录与验收文档建立；开始 TDD 实现。
- 下一步：完成实现与质量门禁，补验收文档 §2-4。

## 7. 复盘

- 做对了什么：
- 踩了什么坑：
- 下次改进：

## 8. 多实例检查

- [ ] 是否有可变成员变量？是否线程安全、跨实例一致？
- [ ] 计数器/限流/预算/冷却是否在 Redis/数据库？
- [ ] 锁是否分布式？是否有超时、防死锁、幂等？
- [ ] 定时任务是否只执行一次？
- [ ] 本地缓存是否可容忍过期/不一致？
