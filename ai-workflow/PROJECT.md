# ai-hub 项目专属规则

> 本文件是项目专属规则；通用规则见 `STANDARDS.md`。**项目规则优先**。

## 基本信息

- 技术栈基线：
  - Java 21 / Maven
  - Spring Boot **3.5.x（目标）**；当前 3.3.11 待升级（LangChain4j 最新 Spring Boot starter 要求 3.5+；若暂不升级则用 LangChain4j core library，不用 starter）
  - Spring MVC + RestClient/WebClient（网关转发保持原始 JSON，不经过 LangChain4j 归一化）
  - PostgreSQL（1.3+：虚拟 Key / 日志 / 预算）
  - Redis + Lettuce（共享状态/限流/预算/冷却）；Redisson（分布式锁）
  - LangChain4j（2 期起：MCP / Prompt / RAG / Memory；网关透传层不替换为 LangChain4j）
  - LangGraph4j（暂不引入；多步骤/多 Agent 有状态编排出现时再评估）
  - Jackson / Jakarta Validation
- 包名：`com.qtua.aihub`
- 默认端口：`18080`（本机 8080 被其他服务占用）
- 当前进度：需求 1.1 已完成；需求 1.2 实现中

## IDE / 运行

- 推荐 **IntelliJ IDEA**：打开 `E:\pro\ai-hub`（或 `pom.xml`）作为 Maven 项目，Project SDK 选 JDK 21。
- Claude Code 插件从项目根目录启动；读取根目录 `CLAUDE.md` → `ai-workflow/`。
- Maven 使用本机 settings.xml；终端运行 `mvn -Pquality verify`。
- `.vscode/settings.json` 是 Cursor 兼容配置，IDEA 不使用。

## 分层与包结构（覆盖通用 §2）

目标包结构（**统一六层**，不另起一套）：

```text
com.qtua.aihub
├── start/          # AiHubApplication、定时任务入口
├── api/            # controller、filter、error、dto（HTTP 契约）
├── service/        # ChatCompletionService / ModelService（校验、DTO↔BO、编排 core）
├── core/           # ChatCompletionCore / routing（路由、编排、事务边界）
├── manager/        # BO↔DO、Repository 门面（1.3+ 有 DB 时）
├── integration/    # UpstreamClient、Redis/LangChain4j 适配
├── dal/            # MyBatis/Repository（1.3+ 有 DB 时）
└── common/         # ErrorResponse、ChatCompletionException、常量、工具
config/             # GatewayProperties、RestClient/Redis 配置（支撑包）
```

- Controller/API 只做 HTTP 契约与响应；Service 做校验、DTO↔BO、编排 Core；Core 做路由/业务编排；Integration 只做外部 HTTP/Redis/LangChain4j 适配；Common 放异常/错误体/常量。
- 当前实现仍在 `gateway` 单包中，属于质量债务，1.2 收尾后统一重构。
- 本期无数据库；1.3 起引入 PostgreSQL/Redis（Redis 已在技术栈基线）。

## 多实例约束（覆盖通用 §2.5）

- 路由选择器不得用成员变量 Map + `synchronized` 保存轮询状态；优先无状态加权随机。
- 如需平滑轮询/冷却/限流/预算，状态放 Redis；单机实现必须在 `PROJECT.md` 标注限制。
- 分布式锁用 Redisson/Redis；本地锁只保护本机状态，不用于跨实例互斥。
- 定时任务/批处理必须 leader 选举或幂等。

## 质量债务（待重构）

- [ ] `gateway` 包按六层拆分为 `start/api/service/core/manager/integration/dal/common`
- [ ] `SmoothWeightedSelector` 去单机状态（无状态加权随机或 Redis 共享）
- [ ] 补类/方法/关键行注释
- [ ] 技术栈补齐：Spring Boot 3.5.x（或 LangChain4j core）、Redis + Lettuce/Redisson、LangChain4j 依赖、docker-compose（Redis/PostgreSQL）
- [ ] 1.2 完成后统一重构并补多实例测试

## 接口契约（覆盖通用 §4）

- 本项目是 **OpenAI 兼容** 接口，不使用标准网关 `Result<T>`。
- 请求：`{model, messages}`；`model` 是别名，转发上游前改写为部署真实模型。
- 响应：OpenAI ChatCompletion 结构；`model` 回写请求别名；`usage` 等字段原样透传。
- 错误体：`{"error":{"message","type","param","code"}}`。
- 完整规格：`specs/001-chat-completions/`；行为基线见知识库历史文档。

## 业务/接口约束

- `POST /v1/chat/completions` 与 `POST /chat/completions`。
- 未知模型 400；未鉴权 401；`stream=true` 暂未实现 400（需求 1.7）。
- 虚拟 Key 只存哈希（1.3）；限流/预算（1.4）；日志计量（1.5）；fallback（1.6）；SSE（1.7）。

## 质量门禁

```bash
mvn -Pquality verify
```

Checkstyle + SpotBugs + ArchUnit + JaCoCo（行覆盖率 ≥ 70%）。

## 配置与密钥

- 密钥只放 `src/main/resources/application-local.yml`（gitignored）或环境变量。
- 本地启动需代理：`-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897`。
- master key 占位：`sk-local-master-key`（仅本地）。

## 记录

- 需求记录：`ai-workflow/records/REQ-*.md`
- 归因报告：`docs/attribution/`
- 规格：`specs/`（spec-kit）；变更：`openspec/`（可选）
