# REQ-1.7 验收：流式（SSE）透传

- 状态：已验收（2026-11-02，理解默认通过）
- 验收人：
- 需求记录：`../records/REQ-1.7.md`
- 需求来源：Vault `05-项目7-第1期-LLM网关核心.md` §REQ-1.7

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] `stream=true` 返回 `text/event-stream`，`curl -N` 可逐块看到 `data:`，末尾 `data: [DONE]`
- [x] 分块结构原样透传（不缓冲整包），`model` 字段回写请求别名
- [x] 流结束后 `call_logs` 写 1 条，usage 取末块 `usage`，`cost`/`spend`/预算结算正确
- [x] 客户端中途断开后上游连接释放（无连接泄漏）
- [x] 流开始前的错误返回标准 JSON 错误；流开始后的错误以 SSE error 事件结束
- [x] 非流式请求行为与 REQ-1.1 一致（回归）
- [x] `mvn -Pquality verify` 通过

**验收命令 / 示例**：

```bash
# 1) 逐块输出，最后出现 [DONE]
curl -N -X POST http://localhost:18080/v1/chat/completions \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"model":"chat-default","stream":true,"messages":[{"role":"user","content":"hi"}]}'

# 2) 流式调用后查日志与 spend
psql -U aihub -d aihub -c "select model_alias, deployment_model, total_tokens, cost, status from call_logs order by id desc limit 1;"

# 3) 测试
mvn -pl ai-hub-integration,ai-hub-tests -am test -Dtest='*Streaming*,*Stream*'
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-1.7.md` §1-3 人向说明（3 分钟）
3. `DESIGN.md` 流式链路增量 + 核心文件 `ChatCompletionController` / `UpstreamClient`（5 分钟）

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. 流式用 `SseEmitter` 逐块透传，绝不缓冲整包；请求注入 `stream_options.include_usage=true` 以拿到末块 usage。
2. 治理延迟收尾：Filter 只预占，流完成/失败时由 Controller 回调统一治理服务结算与写日志。
3. 断开/错误都要有终点：取消上游订阅释放连接；流内错误写 error 事件后结束。

### 3.2 面试 1 分钟讲法（骨架）

```text
流式请求进来后，网关先做鉴权和限流预算预占，然后用 WebClient 订阅上游 SSE，
通过 SseEmitter 逐块透传给客户端，不缓冲整包；请求改写时注入 include_usage，
流结束时解析末块 usage 做一次计价和结算，写一条调用日志。
如果客户端中途断开，就取消上游订阅释放连接；流开始后的错误没法改状态码，就发 error 事件结束。
```

### 3.3 自测题与参考答案

1. 为什么流式请求不能在 Filter 的同步链路里直接结算？
2. 为什么要在请求里注入 `stream_options.include_usage=true`？
3. 流式调用中客户端断开，网关需要做哪些清理？
4. 流已经开始后上游报错，为什么不能返回 502？
5. 流式与非流式在限流、预算、日志上的行为差异是什么？

### 参考答案

1. Filter 在 Controller 返回时同步链路就结束了，而流还在异步写；此时 usage 尚未产生，必须等流结束回调再结算。
2. 流式响应默认可能不返回 usage；没有 usage 就无法按实际 Token 计费，只能记 0 成本，预算与成本看板失真。
3. 取消上游 Flux 订阅（释放 HTTP 连接）、结束 SseEmitter、按已产生用量结算或释放预占、写最终日志，避免线程/连接泄漏。
4. SSE 响应的 HTTP 状态码在首个数据块发送时就已确定，之后无法修改；只能用协议内的 error 事件传达。
5. 限流/预算预占仍在请求前；日志与结算延迟到流结束；usage 取末块；断开时按已发生用量（若有）或释放预占处理。

### 3.4 亲手改的口子（可选）

- 任务：把流式请求的 `stream_options.include_usage` 去掉，观察日志中的 usage/cost 变化。
- 验证方法：修改 `OpenAiProtocolAdapter` 的流式改写分支，重跑 `StreamingIntegrationTest`。

## 4. 回归影响

- REQ-1.1：非流式请求必须保持原行为
- REQ-1.2：路由与 `/models` 不受影响
- REQ-1.3：鉴权不变
- REQ-1.4：流式预占/结算复用，需验证预算不重复扣减
- REQ-1.5：流式日志在结束后补记
- REQ-1.6：流式请求复用部署选择与冷却过滤，但不在流内重试/fallback

## 5. 验收记录

- 验收日期：2026-11-02
- 结论：工程验收通过（`StreamingIntegrationTest` 2 场景 + `ChatCompletionStreamingCoreTest` 3 场景 + `UpstreamClientTest` 流式场景；`mvn -Pquality verify` 全绿）；真实链路 `curl -N` 逐块输出、末块 usage 与日志结算正确；理解验收默认通过
- 遗留问题：流式超时当前为固定 5 分钟，后续可配置化；客户端断开/超时的集成测试可补充
