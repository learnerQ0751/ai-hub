# ai-hub 项目专属规则

> 本文件是项目专属规则；通用规则见 `STANDARDS.md`。**项目规则优先**。

## 基本信息

- 技术栈基线（与 `E:\learing_demo\ai-coder-help` 对齐）：
  - Java 21 / Maven
  - Spring Boot **4.1.1**
  - Spring MVC + RestClient/WebClient（网关转发保持原始 JSON，不经过 LangChain4j 归一化）
  - PostgreSQL（1.3+：虚拟 Key / 日志 / 预算）
  - Redis + Lettuce（共享状态/限流/预算/冷却）；分布式锁用 Redis Lua（1.6 视需要引入 Redisson）
  - LangChain4j **1.22.0**：core + `langchain4j-open-ai-spring-boot4-starter` 1.22.0-beta32 + `langchain4j-mcp` 1.22.0-beta32 + `langchain4j-reactor` 1.1.0-beta7（2 期起使用）
  - LangGraph4j 暂不引入；多步骤/多 Agent 有状态编排出现时再评估
  - Jackson 2（业务对象/网关内部 JSON）+ Spring Boot 4 默认 Jackson 3（Web 序列化）；显式 Jackson 2 Bean 避免注入歧义
- 包名：`com.qtua.aihub`
- 默认端口：`18080`（本机 8080 被其他服务占用）
- 当前进度：需求 1.1 已完成；需求 1.2 代码完成，待理解验收；下一步需求 1.3

## IDE / 运行

- 推荐 **IntelliJ IDEA**：打开 `E:\pro\ai-hub`（或 `pom.xml`）作为 Maven 项目，Project SDK 选 JDK 21。
- Claude Code 插件从项目根目录启动；读取根目录 `CLAUDE.md` → `ai-workflow/`。
- Maven 使用本机 settings.xml；终端运行 `mvn -Pquality verify`。
- 本地运行：`mvn -pl ai-hub-start -am package -DskipTests` 后执行 `ai-hub-start/target/ai-hub-start-*-exec.jar`。
- `.vscode/settings.json` 是 Cursor 兼容配置，IDEA 不使用。

## 模块结构（覆盖通用 §2，对齐监管报送 data-submission）

**Maven 多模块（9 个模块）**：

```text
ai-hub（父 POM）
├── ai-hub-common       异常/常量/配置模型（GatewayProperties）
├── ai-hub-api          Service 接口 + OpenAI 契约 DTO
├── ai-hub-manager      BO（ChatCompletionBO / ChatCompletionResultBO）
├── ai-hub-dal          数据访问（1.3 起填充）
├── ai-hub-integration  上游 HTTP + OpenAI 协议适配
├── ai-hub-core         业务编排 + 路由策略
├── ai-hub-service      ServiceImpl（校验 + DTO↔BO + 编排 core）
├── ai-hub-start        启动类（根包）+ start/controller、filter、error、config
└── ai-hub-tests        集成测试 + ArchUnit + JaCoCo 聚合
```

- 依赖方向：`start → service → core → manager/integration → dal → common`；禁止反向与跨层，ArchUnit 检查
- `api`：Service 接口 + Req/Res DTO；`service`：Impl；`core`：`@Component` 编排 + 路由，不碰报文；`integration`：协议改写 + HTTP；`common`：异常/常量/配置模型
- Controller 只依赖 `api` 接口，实现由 `service` 模块提供；启动类 `com.qtua.aihub.AiHubApplication`（start 模块根包）
- 可执行包：`ai-hub-start/target/ai-hub-start-*-exec.jar`（主 artifact 为普通 jar，供 tests 模块依赖）
- 本期无数据库；1.3 起在 manager/dal 引入 PostgreSQL

## 多实例约束（覆盖通用 §2.5）

- 路由选择器不得用成员变量 Map + `synchronized` 保存轮询状态；优先无状态加权随机。
- 如需平滑轮询/冷却/限流/预算，状态放 Redis；单机实现必须在 `PROJECT.md` 标注限制。
- 分布式锁用 Redisson/Redis；本地锁只保护本机状态，不用于跨实例互斥。
- 定时任务/批处理必须 leader 选举或幂等。

## 质量债务与待办

- [x] 单模块包分层 → Maven 多模块（9 模块，对齐监管报送）
- [x] `SmoothWeightedSelector` 替换为无状态 `WeightedRandomSelector`
- [x] 补类/方法/关键行注释
- [x] 技术栈补齐：Spring Boot 4.1.1、Redis + Lettuce、LangChain4j 1.22.0、docker-compose（Redis/PostgreSQL）
- [x] 1.2 重构 + 多实例/权重测试（41 → 45 个测试）
- [ ] 1.2 理解验收（按新模块结构讲解 / 5 题自测 / 亲手改一个口子）
- [ ] 严格平滑轮询如需：用 Redis 共享状态，不引入单机 Map

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

Checkstyle + SpotBugs（每模块）+ ArchUnit（层依赖/无环）+ JaCoCo 聚合行覆盖率 ≥ 70%（ai-hub-tests）。当前 45 个测试。

## 配置与密钥

- 密钥只放 `src/main/resources/application-local.yml`（gitignored）或环境变量。
- 本地启动需代理：`-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897`。
- master key 占位：`sk-local-master-key`（仅本地）。

## 记录

- 需求记录：`ai-workflow/records/REQ-*.md`
- 归因报告：`docs/attribution/`
- 规格：`specs/`（spec-kit）；变更：`openspec/`（可选）
