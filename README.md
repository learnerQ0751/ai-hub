# ai-hub

面向 Agent 的 AI 中台：网关、工具（MCP）、记忆与 RAG 的统一下沉层。

一个自建的 OpenAI 兼容网关，把大模型调用、MCP 工具、知识库检索与会话记忆收进同一层，并补齐多租户隔离、全链路 Trace、成本计量与审计。

## 能力（第 1–4 期已完成）

| 域 | 能力 |
|---|---|
| 网关 | chat / embeddings 转发，单入口多部署路由、重试与 fallback、SSE 流式、虚拟 Key、限流与预算 |
| MCP | Server 注册与探活、工具聚合发现、调用代理、chat 内工具自动调用、客户端连接池治理 |
| Prompt | 模板与版本管理、运行时渲染接入网关 |
| RAG | 文档接入（pdf/docx/pptx/xlsx/html）、切片与向量化、pgvector 检索与重排、引用溯源生成、RAG/记忆 MCP Server 双协议暴露 |
| 治理 | 多租户与项目隔离、项目级授权、全链路 Trace（分段事件 + TTFT）、用量与成本统计、管理 API、只追加审计日志 |

## 模块

```
ai-hub-common       通用工具与配置
ai-hub-api          对外契约（DTO / 异常）
ai-hub-dal          数据访问（MyBatis + Flyway 迁移）
ai-hub-manager      领域管理服务
ai-hub-integration  上游适配（模型客户端 / MCP / 向量存储）
ai-hub-core         业务核心
ai-hub-service      应用服务（shared / admin / gateway 分面）
ai-hub-start        公共壳（配置 / 过滤器 / 异常处理）
ai-hub-gateway      数据面启动模块 :18080（聊天 / 嵌入 / RAG / MCP / 记忆）
ai-hub-console      控制面启动模块 :18081（Key / 管理 / 文档接入 / 查询；Flyway）
ai-hub-tests        集成测试
```

依赖方向由 ArchUnit 强制：`gateway/console → start/service → core → manager/integration → api → dal → common`。
鉴权物理隔离：gateway 只收虚拟 Key，console 只收 master key。

## 技术栈

Java 21 · Spring Boot 4.1 · LangChain4j 1.22 · PostgreSQL + pgvector · Redis · MyBatis · Flyway · 质量门禁（Checkstyle / SpotBugs / ArchUnit / JaCoCo）

## 快速开始

```bash
# 依赖服务
docker compose up -d

# 必需环境变量（密钥一律走环境变量，不落库不落代码）
export GATEWAY_MASTER_KEY=<管理主密钥>
export GATEWAY_SECRET_KEY=<上游凭据加密密钥>
export DASHSCOPE_API_KEY=<Embedding 密钥>

# 先 console（执行 Flyway，:18081），再 gateway（:18080）
mvn -pl ai-hub-console spring-boot:run
mvn -pl ai-hub-gateway spring-boot:run
```

## 质量

```bash
mvn -Pquality verify
```

包含编译、Checkstyle、SpotBugs、ArchUnit 架构规则、JaCoCo 覆盖率与全部集成测试（H2，无需外部服务即可跑测试）。

## 文档

- 当前状态与数据表速查：[`ai-workflow/STATUS.md`](ai-workflow/STATUS.md)
- 设计基线：[`ai-workflow/DESIGN.md`](ai-workflow/DESIGN.md)
- 开发流程：[`ai-workflow/WORKFLOW.md`](ai-workflow/WORKFLOW.md)
- 单需求验收：[`ai-workflow/acceptance/`](ai-workflow/acceptance/)

## License

[MIT](LICENSE)
