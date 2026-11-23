# REQ-4.3 验收：成本与用量统计

- 状态：待验收（2026-11-16，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-4.3.md`

## 1. 需求验收标准

> 实现前冻结，唯一勾选点。

- [x] 聚合视图 `v_usage_daily`：天 / tenant_id / project_id / model_alias / key_id /
      requests / prompt_tokens / completion_tokens / total_tokens / cost
- [x] `GET /v1/usage` 支持 from/to/tenant/project/model/key 过滤，返回汇总、明细与按天趋势
- [x] 成本口径与 REQ-1.5 价格表一致（BigDecimal，NUMERIC(18,8)）；缺价上游模型在
      `unpriced_models` 单列告警
- [x] 用量导出 CSV：`GET /v1/usage/export`，与 JSON 同口径
- [x] 聚合实现选择「实时视图」并记录决策（无定时任务/重复聚合）
- [x] 按租户过滤不串数据；虚拟 Key 强制绑定租户/项目/自己的 Key，跨租户 403
- [x] 趋势在时间范围内正确；日期边界为闭区间、默认近 7 天、最长 366 天
- [x] 聚合数值与逐条日志可复算（集成测试注入 3 条日志，断言 requests/tokens/cost）
- [x] `mvn -Pquality verify` 全绿

**验收命令**：

```bash
curl "http://localhost:18080/v1/usage?from=2026-11-01&to=2026-11-16&tenant_id=tenant-a" \
  -H "Authorization: Bearer $MASTER"
curl "http://localhost:18080/v1/usage/export?from=2026-11-01&to=2026-11-16" \
  -H "Authorization: Bearer $MASTER" -o usage.csv
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-4.3.md` §1-3（3 分钟）
3. 核心文件（5 分钟）：`UsageServiceImpl`、`UsageMapper.xml`、`V12__usage_view.sql`

## 3. 实现要点（按需阅读）

### 3.1 三条关键实现要点

1. **视图口径**：直接对 call_logs 按天/租户/项目/模型/Key 聚合，保证「能逐条复算」。
2. **可见性收敛**：虚拟 Key 的 tenant/project/key 都由服务端覆盖，请求其他租户 403。
3. **缺价告警**：用价格目录判断上游模型是否缺价，而不是用 cost=0（免费模型也会是 0）。

### 3.2 面试 1 分钟讲法

```text
用量不额外维护汇总任务，而是对调用日志建按天维度的实时视图，接口按租户/项目/模型/Key
过滤后返回汇总、明细和趋势。这样任何数字都能回到逐条日志复算。虚拟 Key 只能看自己的租户
和 Key；价格目录里没有的上游模型单独列出来告警，避免悄悄按 0 计费。
```

