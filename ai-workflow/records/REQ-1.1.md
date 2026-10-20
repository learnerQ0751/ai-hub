# REQ-1.1：统一入口 + 单模型转发（非流式）

- 日期：2026-10-19
- 状态：已完成
- 关联规格：`specs/001-chat-completions/`
- 接口：POST /v1/chat/completions, POST /chat/completions
- 提交：60777b0

## 需求

- 背景：项目 7 第 1 期 LLM 网关核心，第一个可演示闭环。
- 范围：`POST /v1/chat/completions` 与 `POST /chat/completions`；YAML 配置模型别名；单部署转发 DeepSeek；响应原样透传；规范错误体；请求 ID。
- 不做什么：多部署路由、虚拟 Key、限流预算、日志计量、fallback、SSE（后续需求）。

## 验收标准

- [x] 200：返回 OpenAI ChatCompletion 结构，`model=chat-default`，`usage` 完整。
- [x] 未知模型 400：`invalid_request_error` / `param=model`。
- [x] 未鉴权 401：`auth_error`。
- [x] `stream=true` 400：明确提示流式暂未实现。
- [x] `mvn -Pquality verify` 通过；单测 18 个；行覆盖率 84.97%。
- [x] 真实 DeepSeek 链路验证通过。

## 决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 请求 `model` 转发前改写为部署真实模型 | 上游只认真实模型名，别名会 400 | 2026-10-19 |
| 响应 `model` 回写为请求别名 | 对齐 LiteLLM 正常路径行为 | 2026-10-19 |
| 默认端口 18080 | 本机 8080 被其他服务占用 | 2026-10-19 |
| Auth 先做 master key 占位 | 需求 1.3 再实现虚拟 Key | 2026-10-19 |
| `stream=true` 明确拒绝 | 流式在需求 1.7 实现 | 2026-10-19 |

## 执行日志

### 2026-10-19

- 完成 Spring Boot 骨架、配置绑定、UpstreamClient、Service、Controller、错误体、RequestIdFilter、AuthFilter。
- 单测：GatewayProperties / ErrorResponse / Controller / Service / UpstreamClient / RequestIdFilter / ArchUnit。
- 真实链路：200 / 400 / 401 / 400（stream）全部符合预期。
- 提交：`60777b0` 功能；`1389f38` 文档；`2cef0cf` 归因；`aa59b16` 任务勾选。

### 2026-10-20 基础重构

- 代码从 `gateway` 混合包迁至六层结构（start/api/service/core/integration/common/config）；接口与行为不变。
- 技术栈升级：Spring Boot 4.1.1 + LangChain4j 1.22.0 + Redis/Lettuce；Jackson 2/3 分层处理。
- 当前 41 个测试、Checkstyle 0 违规、SpotBugs/覆盖率门禁通过。

## 复盘

- 做对了：TDD + 硬门禁 + 真实链路验收；单测全绿仍可能被真实上游暴露问题。
- 踩坑：上游收到别名 400；响应 model 语义不一致；8080 冲突；ArchUnit 扫测试类假环；SpotBugs 构造器注入误报。
- 下次改进：先做一次真实链路冒烟，再补单测细节；端口/代理等环境项提前确认。

## 理解验收

- 我学到的 3 条：
  1. （历史需求，待补）
  2. （历史需求，待补）
  3. （历史需求，待补）
- 自测题得分：
- 我亲手改的口子：

## 归因（可选）

- 提交 `60777b0`：1523 行 AI，0 human，0 unknown（`claude::deepseek-flash`）。
- 文档提交 `1389f38`：362 行 AI，2418 行 unknown（复制的 spec-kit 命令文件），0 human。
- prompt：机制已就绪；需从仓库根目录启动 agent 且安装后重启会话。
