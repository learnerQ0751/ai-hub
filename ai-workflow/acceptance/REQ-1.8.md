# REQ-1.8 验收：对外错误信息中文化

- 状态：已验收（2026-11-03）
- 验收人：
- 需求记录：`../records/REQ-1.8.md`
- 需求来源：用户第一期验收反馈（2026-11-03）

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] 未知模型 400：`error.message` 为中文（含「模型」），`type=invalid_request_error`、`param=model`、`code=400` 不变
- [x] 未鉴权 / Key 封禁 / Key 过期 401：`error.message` 为中文（含「鉴权」「封禁」「过期」）
- [x] 越权模型 403：`error.message` 为中文（含「无权」/「不可用」），`type=key_model_access_denied` 不变
- [x] 限流 429：`error.message` 为中文（含「限流」「频繁」）；预算 400：含「预算」
- [x] 上游 401 / 429 / 4xx / 5xx / 超时：`error.message` 为中文（含「上游」「超时」）
- [x] `/key/*` 管理接口错误（缺少字段、Key 不存在等）`error.message` 为中文
- [x] `error.type` / `param` / `code`、HTTP 状态码全部保持英文协议值与原值
- [x] `mvn -Pquality verify` 通过

**验收命令 / 示例**：

```bash
# 未知模型
curl -s -X POST http://localhost:18080/v1/chat/completions \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"model":"no-such-model","messages":[{"role":"user","content":"hi"}]}'
# 期望：{"error":{"message":"模型 'no-such-model' 不存在或不可用...","type":"invalid_request_error","param":"model","code":"400"}}

# 未鉴权
curl -s -X POST http://localhost:18080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"chat-default","messages":[{"role":"user","content":"hi"}]}'
# 期望 message 为中文鉴权提示，type=auth_error、code=401 不变
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-1.8.md` §1-3（3 分钟）
3. 核心文件 `ChatCompletionException` / `ErrorResponse` / `GatewayExceptionHandler`（5 分钟）

## 3. 实现要点（按需阅读）

### 3.0 文案语言边界

```mermaid
flowchart LR
    A["错误响应"] --> B["error.message：中文（面向用户）"]
    A --> C["error.type / param / code：英文（协议契约）"]
    A --> D["HTTP 状态码：数字（协议契约）"]
    A --> E["服务端日志：英文（面向运维）"]
```

### 3.1 三条关键实现要点

1. 只改 `message`；`type`/`param`/`code` 与 HTTP 状态码不动，保证 OpenAI SDK 兼容。
2. 文案集中：常规错误在 `ChatCompletionException` 工厂方法；请求解析/Key 管理/治理错误在各自调用点改为中文。
3. 技术专有名词保留原文（API Key、Redis、SSE、JSON），句子用中文。

### 3.2 面试 1 分钟讲法（骨架）

```text
网关对外错误体分两层：message 给人看，type/param/code 给程序看。
我们按客群把 message 统一成中文，结构化字段保持英文协议值不变，
这样既让中文用户直接看懂，也不破坏 OpenAI SDK 的兼容性；
文案集中维护，避免各接口各自写一套。
```

## 4. 回归影响

- 所有需求的 HTTP 状态码与 `error.type` 必须保持不变
- OpenAI SDK 兼容：错误体结构不变，仅 message 内容语言变化
- 前端/调用方若硬编码英文 message 做判断，需改为按 type/code 判断（规范要求）

## 5. 验收记录

- 验收日期：2026-11-03
- 结论：工程验收通过（`ChatCompletionExceptionTest` 2 组断言 + `ChatCompletionControllerTest`/`KeyControllerTest` 中文 message 断言 + `KeyServiceTest` 中文断言；`mvn -Pquality verify` 全绿）。真实链路：未知模型 400、未鉴权 401、`/key/info` 缺参 400、方法不支持 405 均为中文 message；`type`/`param`/`code` 与状态码未变。
- 遗留问题：无
