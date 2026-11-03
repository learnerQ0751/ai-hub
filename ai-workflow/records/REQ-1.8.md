# REQ-1.8：对外错误信息中文化

- 日期：2026-11-03
- 状态：已完成
- 关联规格：无（横切文案修复）
- 依赖需求：REQ-1.1 ~ 1.7（覆盖全部错误响应）
- 需求来源：用户第一期验收反馈（2026-11-03）：客群为中文使用者，错误信息不应为英文
- 接口：所有错误响应的 `error.message` 字段改为中文；`error.type` / `param` / `code` 保持协议英文

## 1. 是什么

网关对外返回的错误体是 OpenAI 风格：

```json
{"error":{"message":"...","type":"invalid_request_error","param":"model","code":"400"}}
```

本次只改 `message`（给人看的中文说明）；`type`、`param`、`code` 是客户端/SDK 依赖的结构化协议字段，保持英文不变。日志仍保持英文，便于运维检索。

## 2. 有什么用

- 客群是中文使用者，错误提示必须能直接看懂，不需要二次翻译。
- 结构化字段不变，保证 OpenAI SDK 兼容与已有客户端不受影响。
- 把「面向用户的文案语言」固化为工作流规范，后续需求不再回退为英文。

## 3. 怎么实现的

- 集中修改 `ChatCompletionException` 各工厂方法的中文 message。
- 修改调用点传入的 message：请求解析、Key 管理、治理过滤器、限流/预算、上游错误。
- `type` / `param` / `code` 不动；HTTP 状态码不动。
- 测试：新增中文 message 断言；更新原英文断言（`KeyServiceTest`）。

## 4. 影响面

```yaml
capability:
  type: cross-cutting
  entry_point: ErrorResponse / GatewayExceptionHandler / AuthFilter / GatewayGovernanceFilter
  interface_agnostic: true
modules:
  add: []
  change: [common, integration, service, start]
interfaces:
  add: []
  change:
    - "全部错误响应的 error.message 改为中文（type/param/code 不变）"
  remove: []
tables:
  add: []
  change: []
config: []
depends_on: [REQ-1.1, REQ-1.2, REQ-1.3, REQ-1.4, REQ-1.5, REQ-1.6, REQ-1.7]
compat: extension
gate: confirm
regression:
  - 所有 REQ 的 HTTP 状态码与 error.type 保持不变
  - OpenAI SDK 兼容性（仅 message 内容变化）
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 只改 `error.message`，`type`/`param`/`code` 保持英文 | 后三者是协议契约，客户端按值分支；改语言会破坏兼容 | 2026-11-03 |
| 日志保持英文 | 面向运维/检索，避免日志与错误体耦合；需要中文排查时看 message 即可 | 2026-11-03 |
| 技术专有名词保留原文（API Key、Redis、SSE、JSON） | 中文客群也通用，强行翻译反而降低可读性 | 2026-11-03 |
| 规范落点：STANDARDS（通用）+ PROJECT（本项目客群）+ WORKFLOW（完成定义检查） | 一处规则、一处项目覆盖、一处执行检查，防止回退 | 2026-11-03 |

## 6. 执行摘要

### 2026-11-03

- 进展：完成 `ChatCompletionException` 全部工厂方法与调用点（请求解析/Key 管理/治理/限流预算/上游错误/流式）中文文案改造；补充 `GatewayExceptionHandler`（405/请求体解析/缺参/类型不匹配/内部错误）中文；新增 `ChatCompletionExceptionTest`（中文 message + 协议字段英文两组断言）；更新 `KeyServiceTest`/`ChatCompletionControllerTest`/`KeyControllerTest`；`mvn -Pquality verify` 全绿。
- 真实链路：未知模型 400、未鉴权 401、`/key/info` 缺参 400、方法不支持 405 均返回中文 message；`type`/`param`/`code` 保持英文协议值。
- 规范：STANDARDS §3.6 用户文案语言 + PROJECT 错误契约 + WORKFLOW 完成定义 + ACCEPTANCE 模板已同步。
- 下一步：后续需求按新规范验收。

## 7. 复盘

- 做对了什么：文案集中改在异常工厂，调用点只改必要参数；先用测试锁定「message 中文 + type/param/code 英文」边界，避免误改协议字段。
- 踩了什么坑：一期实现时只关注功能，未把「客群语言」纳入验收；错误文案分散在多个调用点，需要逐处盘点。
- 下次改进：新项目初始化时就把客群语言写入 PROJECT.md；acceptance 模板已加提示，后续按模板执行。

## 8. 多实例检查

- [x] 是否有可变成员变量？无
- [x] 计数器/限流/预算/冷却是否在 Redis/数据库？不涉及
- [x] 锁是否分布式？不涉及
- [x] 定时任务是否只执行一次？不涉及
- [x] 本地缓存是否可容忍过期/不一致？不涉及
