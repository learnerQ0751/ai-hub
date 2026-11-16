# REQ-4.2 验收：全链路 Trace

- 状态：待验收（2026-11-16，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-4.2.md`

## 1. 需求验收标准

> 实现前冻结，唯一勾选点。

- [x] `trace_id` 贯穿 Filter→Service→Core→上游：入口生成，写 MDC 与响应头
      `X-Trace-Id`（与 `X-Request-Id` 同值）
- [x] 分段事件：gateway（路径/状态/耗时）、rag（query 长度、候选、chunk id/score、noHit）、
      model（别名/部署/tokens/cost）、answer（引用 id/用量）、mcp（工具/Server/结果长度）
- [x] `GET /v1/traces/{trace_id}` 按时间顺序返回分段；master 可查任意，虚拟 Key 仅限绑定租户
- [x] 日志异步写入不阻塞响应；采样率 `gateway.trace.sample-rate` 可配置，按 trace 确定性采样
- [x] 预留 `TraceRecorder` 端口，可替换为 OpenTelemetry/Micrometer 实现
- [x] 一次 RAG 请求可查到 gateway/rag/model/answer 四段（集成测试）
- [x] MCP 调用与 REST 调用同构（`McpCallServiceTraceTest` 断言 segment=mcp、tenant/key）
- [x] 响应头 trace_id 与查询结果一致
- [x] `mvn -Pquality verify` 全绿

**验收命令**：

```bash
curl -X POST http://localhost:18080/v1/rag/answer -H "Authorization: Bearer $KEY" \
  -H 'Content-Type: application/json' \
  -d '{"query":"年假几天","project_id":"p1","model":"chat"}' -D /tmp/headers
TRACE=$(grep -i x-trace-id /tmp/headers | awk '{print $2}' | tr -d '\r')
curl http://localhost:18080/v1/traces/$TRACE -H "Authorization: Bearer $KEY"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-4.2.md` §1-3（3 分钟）
3. 核心文件（5 分钟）：`TraceRecorderImpl`、`TraceIntegrationTest`、`V11__trace_events.sql`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **一个 ID**：request_id 即 trace_id，入口写 MDC 与响应头；所有分段共用。
2. **分段摘要**：每段只记排障需要的摘要（路径、命中 id/score、tokens、cost、引用 id），不写正文。
3. **异步与采样**：有界线程池异步写，按 trace 确定性采样，写失败不影响业务。

### 3.2 面试 1 分钟讲法

```text
每个请求入口生成 trace_id，写进 MDC 和响应头；网关、RAG 召回、模型调用、回答引用、MCP 调用
各写一条摘要事件到 trace_events，异步落库。查询接口按 trace_id 聚合，虚拟 Key 只能看自己租户。
采样按 trace 哈希做，保证一次请求的段要么全留要么全丢；写入失败只记日志，不影响业务。
```

### 3.3 自测题与参考答案

1. 为什么 trace_id 不单独再生成一个？
2. 为什么按 trace 采样而不是按事件采样？
3. payload 里为什么不能放 prompt 正文？
4. 队列满时怎么办？
5. 如何把 Trace 接到 OpenTelemetry？

### 参考答案

1. 现有 request_id 已贯通 Filter/日志/调用日志，复用可减少一个 ID 体系和关联成本。
2. 逐事件随机会把同一次请求的段采样成残缺轨迹，无法排障。
3. 正文可能含用户隐私/密钥，trace 是长期保留的排障数据，应只存摘要。
4. 退回调用线程同步写入，宁可多花一点时间也不丢排障数据。
5. 实现 `TraceRecorder` 端口，在 `record` 中创建/结束 OTel span，业务代码不变。

## 4. 亲手改口子（可选）

- 把 `gateway.trace.sample-rate` 改为 0，验证不再产生新事件。
- 用另一个租户的 Key 查同一 trace_id，验证 404。
