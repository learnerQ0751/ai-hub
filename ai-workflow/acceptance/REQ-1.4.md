# REQ-1.4 验收：限流与预算（Redis 实现）

- 状态：已验收（2026-11-02，理解默认通过）
- 验收人：
- 需求记录：`../records/REQ-1.4.md`
- 需求来源：Vault `05-项目7-第1期-LLM网关核心.md` §REQ-1.4

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] Key `rpm_limit=2`：连续第 3 次请求返回 429（`error.type=rate_limit_error`）；窗口恢复后再次 200
- [x] `tpm_limit` 超限返回 429；结算后的 token 统计与 REQ-1.5 的 `usage` 一致
- [x] 预算耗尽后请求被拒绝（400，`error.type=budget_exceeded`）；跨预算周期后恢复
- [x] 两个应用实例共享同一 Redis，并发请求下计数一致（并发压测验证）
- [x] Redis 不可用时默认 fail-open 并记录 WARN；可通过配置改为 fail-closed
- [x] 限流/预算命中写入 `call_logs`（`status` + `error_type`），可供后续统计
- [x] `mvn -Pquality verify` 通过

**验收命令 / 示例**：

```bash
# 1) 生成带限流的 Key
curl -X POST http://localhost:18080/key/generate \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"models":["chat-default"],"rpmLimit":2,"tpmLimit":1000,"maxBudget":0.01,"budgetDuration":"daily"}'

# 2) 连续调用三次（第三次应 429）
curl -i -X POST http://localhost:18080/v1/chat/completions \
  -H "Authorization: Bearer <虚拟 Key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"chat-default","messages":[{"role":"user","content":"hi"}]}'

# 3) 查看 Key 限流/预算字段
curl "http://localhost:18080/key/info?key=<虚拟 Key>" \
  -H "Authorization: Bearer sk-local-master-key"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-1.4.md` §1-3 人向说明（3 分钟）
3. `DESIGN.md` 链路增量 + 核心文件 `RedisRateLimiter` / `ChatCompletionCore` / `KeyServiceImpl`（5 分钟）

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. 限流在请求路径：rpm/tpm 用 Redis ZSET + Lua 原子滑动窗口，按 `key + model` 维度，窗口 60s。
2. tpm/预算采用"请求前预占、响应后结算"：输出 token 未知，用 `max_tokens` 上限预占；实际 usage 结算差额。
3. 预算金额用整数 micro 单位存 Redis，周期 key 带 TTL（daily/monthly），跨期自动切换。

### 3.2 面试 1 分钟讲法（骨架）

```text
我给网关的虚拟 Key 加了两层治理：限流和预算。限流用 Redis ZSET 做 60 秒滑动窗口，
按 key+model 维度原子判断 RPM/TPM，超限返回 429；TPM 在请求前按输入估算加 max_tokens 预占，
响应后按实际 usage 结算。预算按 key 的 max_budget 和周期（日/月）累计成本，
请求前预占、响应后结算，金额用整数 micro 单位避免浮点误差。多实例共享 Redis 计数一致，
Redis 故障默认 fail-open 并告警。
```

### 3.3 自测题与参考答案

1. 为什么 tpm 要"预占 + 结算"，而不是直接按实际 usage 记录？
2. 滑动窗口用 ZSET 实现时，member 里为什么要带 token 数？如何避免重复计数？
3. 预算的周期 key 如何设计？为什么不用定时任务清零？
4. Redis 故障时为什么默认 fail-open？什么场景应该 fail-closed？
5. 两个实例同时请求时，如何保证 rpm 不超发？

### 参考答案

1. 请求前不知道输出 token；不预占就无法在调用前拦截，只能事后发现超限。预占用 max_tokens 上限保证不超发，结算按实际 usage 保证统计准确。
2. ZSET 的 score 存时间戳，member 需要携带本次 token 数（如 `requestId|tokens`），统计时遍历窗口求和；结算时先删除旧的预占 member 再加入实际 member，用 requestId 保证幂等。
3. daily key 用 `yyyyMMdd`、monthly 用 `yyyyMM`，带 TTL（如 2 天/32 天）；跨期自然换 key，无需定时清零，也不会因任务失败导致数据错乱。
4. 限流是保护上游/成本，不是业务正确性；Redis 故障时放行可避免整体不可用（fail-open）。金融/安全场景或预算硬约束可配置 fail-closed。
5. 用 Redis Lua 脚本把"清理窗口 + 统计 + 判断 + 写入"放在一次原子执行中，多实例共享同一 key，天然串行。

### 3.4 亲手改的口子（可选）

- 任务：把 `gateway.rate-limit.fail-open` 改为 `false`，停掉 Redis，重跑限流集成测试，观察请求是被放行还是拒绝。
- 验证方法：`mvn -pl ai-hub-tests -am test -Dtest=RateLimitIntegrationTest`。

## 4. 回归影响

- REQ-1.1：无 Key 限流/预算时聊天链路保持原行为
- REQ-1.2：路由与 `/models` 不受影响
- REQ-1.3：`/key/generate`、`/key/info` 字段扩展保持兼容
- REQ-1.5：计量与 spend 继续生效；tpm 结算口径与 usage 一致

## 5. 验收记录

- 验收日期：2026-11-02
- 结论：工程验收通过；理解验收默认通过（如需深挖看 §3）。真实链路：rpm=2 第三次 429、预算 400、tpm 429、日志 error_type 正确。
- 遗留问题：B-10（LiteLLM 超限错误体）未实测，当前按 `rate_limit_error`/`budget_exceeded` 语义实现；流式请求的 TPM 结算留待 REQ-1.7；预算预占幂等/过期清理由 core 单次调用保障，生产化时可补补偿任务。
