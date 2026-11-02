# REQ-1.1：统一入口 + 单模型转发（非流式）

- 日期：2026-10-19
- 状态：已完成
- 关联规格：`specs/001-chat-completions/`
- 依赖需求：无
- 需求来源：Vault `05-项目7-第1期-LLM网关核心.md` §REQ-1.1
- 接口：POST /v1/chat/completions, POST /chat/completions
- 提交：60777b0

## 1. 是什么

项目 7 的第一个可演示闭环：对外提供 OpenAI 兼容的聊天补全入口，把请求按模型别名转发到 DeepSeek，并原样返回上游响应。这是后续网关治理能力（路由、Key、限流、计量）的地基。

## 2. 有什么用

- 业务价值：统一入口是网关的最小可用形态；所有治理能力叠加在它之上。
- 对应参照行为：LiteLLM B-01（`/chat/completions` 与 `/v1/chat/completions` 均可用，响应含 `usage`）。
- 面试叙事点：为什么网关要做别名改写、为什么要统一错误体、为什么要请求 ID。

## 3. 怎么实现的

- 分层落点：`start/controller` → `api` 接口 → `service`（校验 + DTO↔BO）→ `core`（编排）→ `integration`（协议 + HTTP）。
- 核心类：`ChatCompletionController`、`ChatCompletionServiceImpl`、`UpstreamClient`（重构后并入 `OpenAiProtocolAdapter`）。
- 请求链路：

```text
Filter（RequestId/Auth）→ Controller → Service（校验 + 组 BO）→ Core（别名→真实模型）
→ Integration（HTTP 调上游）→ 响应 model 回写别名 → 返回原始 JSON
```

## 4. 影响面

```yaml
modules:
  add: [common, api, integration, core, service, start]
  change: []
interfaces:
  add: ["POST /v1/chat/completions", "POST /chat/completions"]
  change: []
  remove: []
tables:
  add: []
  change: []
config: [gateway.providers, gateway.model-list]
depends_on: []
compat: additive
gate: auto
regression: []
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 请求 `model` 转发前改写为部署真实模型 | 上游只认真实模型名，别名会 400 | 2026-10-19 |
| 响应 `model` 回写请求别名 | 对齐 LiteLLM 正常路径行为 | 2026-10-19 |
| 默认端口 18080 | 本机 8080 被其他服务占用 | 2026-10-19 |
| Auth 先做 master key 占位 | 虚拟 Key 在 REQ-1.3 实现 | 2026-10-19 |
| `stream=true` 明确拒绝 | 流式在 REQ-1.7 实现 | 2026-10-19 |

## 6. 执行摘要

### 2026-10-19

- 完成 Spring Boot 骨架、配置绑定、UpstreamClient、Service、Controller、错误体、RequestIdFilter、AuthFilter。
- 单测环绕；真实链路 200 / 400 / 401 / 400（stream）全部符合预期。
- 提交：`60777b0` 功能；`1389f38` 文档；`2cef0cf` 归因；`aa59b16` 任务勾选。

### 2026-10-20 基础重构

- 代码从 `gateway` 混合包迁至六层结构；接口与行为不变。
- 技术栈升级：Spring Boot 4.1.1 + LangChain4j 1.22.0 + Redis/Lettuce；Jackson 2/3 分层处理。

## 7. 复盘

- 做对了：TDD + 硬门禁 + 真实链路验收；单测全绿仍可能被真实上游暴露问题。
- 踩坑：上游收到别名 400；响应 model 语义不一致；8080 冲突；ArchUnit 扫测试类假环；SpotBugs 构造器注入误报。
- 下次改进：先做真实链路冒烟，再补单测细节；端口/代理等环境项提前确认。

## 8. 多实例检查

- [x] 无可变业务状态；Filter/Service/Core 均为无状态组件
- [x] 无计数器/限流/预算/冷却（本需求不涉及）
- [x] 无本地锁
- [x] 无定时任务
- [x] 无本地缓存

## 归因（可选）

- 提交 `60777b0`：1523 行 AI，0 human，0 unknown（`claude::deepseek-flash`）。
- 文档提交 `1389f38`：362 行 AI，2418 行 unknown（spec-kit 命令文件），0 human。
