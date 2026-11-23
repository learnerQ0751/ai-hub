# ai-hub 项目专属规则

> 本文件是项目专属规则；通用规则见 `docs/STANDARDS.md`。**项目规则优先**。

## 基本信息

- 技术栈基线（与本项目既有工程一脉相承，不引自其他仓库）：
  - Java 21 / Maven
  - Spring Boot **4.1.1**
  - Spring MVC + RestClient/WebClient（网关转发保持原始 JSON，不经过 LangChain4j 归一化）
  - PostgreSQL（1.3+：虚拟 Key / 日志 / 预算）；**数据库迁移：Flyway**（`spring-boot-starter-flyway` + `flyway-database-postgresql`；`db/migration/Vn__*.sql`，启动自动迁移）
  - **持久层：MyBatis（`mybatis-spring-boot-starter` 4.1.0，对应 Spring Boot 4.1）**；SQL 只在 `dal` 的 Mapper/XML；**禁止 JdbcTemplate / JdbcClient / Spring Data JPA 等替代实现**
  - 测试数据库：H2 PostgreSQL 兼容模式（仅测试）；生产 PostgreSQL
  - Redis + Lettuce（共享状态/限流/预算/冷却）；分布式锁用 Redis Lua（1.6 视需要引入 Redisson）
  - LangChain4j **1.22.0**：core + `langchain4j-open-ai-spring-boot4-starter` 1.22.0-beta32 + `langchain4j-mcp` 1.22.0-beta32 + `langchain4j-reactor` 1.1.0-beta7（2 期起使用）
  - LangGraph4j 暂不引入；多步骤/多 Agent 有状态编排出现时再评估
  - Jackson 2（业务对象/网关内部 JSON）+ Spring Boot 4 默认 Jackson 3（Web 序列化）；显式 Jackson 2 Bean 避免注入歧义
- 包名：`com.qtua.aihub`
- 模型目录端口：`common/catalog`（`ModelCatalog`/`ModelDeployment`），YAML 实现 `manager/catalog/YamlModelCatalog`；业务层禁止直接依赖 `GatewayProperties`
- 默认端口：`18080`（本机 8080 被其他服务占用）
- 当前进度不写在这里：以 `ai-workflow/STATUS.md`（脚本生成）为准，避免两处不同步
- 验收入口：每个需求的人向验收文档在 `ai-workflow/acceptance/REQ-x.y.md`；全局状态看 `ai-workflow/STATUS.md`，设计基线看 `ai-workflow/DESIGN.md`
- 数据模型与表速查：`ai-workflow/docs/DATA-MODEL.md`（STATUS 的「数据表速查」由此生成）

## IDE / 运行

- 推荐 **IntelliJ IDEA**：打开仓库根目录（`pom.xml` 所在处的克隆路径）作为 Maven 项目，Project SDK 选 JDK 21。
- Claude Code 插件从项目根目录启动；读取根目录 `CLAUDE.md` → `ai-workflow/`。
- Maven 使用本机 settings.xml；终端运行 `mvn -Pquality verify`。
- 本地运行：`mvn -pl ai-hub-gateway,ai-hub-console -am package -DskipTests` 后先执行
  `ai-hub-console/target/ai-hub-console-*-exec.jar`（执行 Flyway），再执行
  `ai-hub-gateway/target/ai-hub-gateway-*-exec.jar`。
- `.vscode/settings.json` 是 Cursor 兼容配置，IDEA 不使用。

## 模块结构（覆盖通用 §2）

**Maven 多模块（11 个模块，REQ-5.1 数据面/控制面拆分）**：

```text
ai-hub（父 POM）
├── ai-hub-common       异常/常量/配置模型（GatewayProperties）
├── ai-hub-api          Service 接口 + OpenAI 契约 DTO
├── ai-hub-manager      BO（ChatCompletionBO / ChatCompletionResultBO）
├── ai-hub-dal          数据访问 + Flyway 迁移脚本
├── ai-hub-integration  上游 HTTP + OpenAI 协议适配
├── ai-hub-core         业务编排 + 路由策略
├── ai-hub-service      ServiceImpl；shared / admin / gateway 子包按面归属
├── ai-hub-start        公共壳：application-common.yml、RequestIdFilter、error、config
├── ai-hub-gateway      数据面启动模块：9 个 Controller、AuthFilter、GatewayGovernanceFilter、/mcp
├── ai-hub-console      控制面启动模块：8 个 Controller、AdminAuthFilter；Flyway 唯一执行方
└── ai-hub-tests        集成测试 + ArchUnit + JaCoCo 聚合
```

- 依赖方向：`gateway/console → start/service → core → manager/integration → dal → common`；禁止反向与跨层，ArchUnit 检查
- 数据面只装载 `service.shared` + `service` 根包 + `service.gateway`（正则排除 `service.admin`）；控制面只装载 `service.shared` + `service.admin`，排除 `service.gateway` 与 RAG 检索核心
- 鉴权物理隔离：gateway 只收虚拟 Key（master key 403）；console 只收 master key（虚拟 Key 403）；`/actuator/**` 免鉴权
- 两个启动类：`com.qtua.aihub.gateway.GatewayApplication`、`com.qtua.aihub.console.ConsoleApplication`
- 可执行包：`ai-hub-gateway/target/ai-hub-gateway-*-exec.jar`、`ai-hub-console/target/ai-hub-console-*-exec.jar`
- 两应用共享同一数据库与 dal/common；跨应用事务不存在，控制面写入必须幂等（REQ-5.2）

## 关键设计决策（2026-10-30 拍板）

- **rpm/tpm 语义分层**：部署级仅作路由权重（领域模型 `weight`）；Key 级 `rpm_limit` / `tpm_limit` 作限流阈值（REQ-1.4）。
- **容错**：REQ-1.6 实现别名级 `fallbacks` 降级链 + 部署 `priority` 主备顺序，先主备、后 fallback。
- **上游 api-key 加密**：AES-256-GCM + 环境密钥 `GATEWAY_SECRET_KEY`，密文入库；管理接口永不回显。
- **master key**：`prod` / `production` profile 下为空启动失败（`MasterKeyStartupValidator`）；local 放行并 WARN。

## 技术栈约束（MUST）

- 本文件是技术选型的唯一裁决点；实现阶段**不得自行新增/替换/降级框架**
- 选型缺失或不确定 → **停止实现，先问用户**，写入本文件后再动手
- 违反示例（禁止）：以"本期只有一张表/更轻量"为由用 JdbcClient 替代 MyBatis；临时引入未登记的 HTTP/JSON/缓存库；同类框架混用
- 依赖版本统一在根 `pom.xml` 管理（`dependencyManagement` / properties）；模块 pom 不单独写第三方版本（由 Spring Boot BOM 或根属性管理）

## 多实例约束（覆盖通用 §2.5）

- 路由选择器不得用成员变量 Map + `synchronized` 保存轮询状态；优先无状态加权随机。
- 如需平滑轮询/冷却/限流/预算，状态放 Redis；单机实现必须在 `PROJECT.md` 标注限制。
- 分布式锁用 Redisson/Redis；本地锁只保护本机状态，不用于跨实例互斥。
- 定时任务/批处理必须 leader 选举或幂等。
- 资源指标归属：业务计数（限流/预算/冷却/会话）→ Redis/DB；本地物理资源指标（连接数/线程数/本机子进程数）→ 每实例指标（Micrometer/统计端点），监控端聚合，**禁止写 Redis 伪造全局状态**（STANDARDS §2.7）。

## 质量债务与待办

- [x] 单模块包分层 → Maven 多模块（9 模块；REQ-5.1 起 11 模块）
- [x] 数据面/控制面拆分：双启动包、鉴权物理隔离、Flyway 只由 console 执行（REQ-5.1）
- [x] `SmoothWeightedSelector` 替换为无状态 `WeightedRandomSelector`
- [x] 补类/方法/关键行注释
- [x] 技术栈补齐：Spring Boot 4.1.1、Redis + Lettuce、LangChain4j 1.22.0、docker-compose（Redis/PostgreSQL）
- [x] 1.2 重构 + 多实例/权重测试（41 → 45 个测试）
- [x] 1.3 虚拟 Key：api_keys/哈希鉴权/白名单/封禁/info（88 个测试）
- [x] 1.2 / 1.3 理解验收（2026-10-30，默认通过）
- [x] 1.4 限流预算 / 1.5 日志计量 / 1.6 容错 / 1.7 流式（第 1 期完成）
- [x] MCP 客户端资源治理（REQ-2.10：显式连接池 / stdio IO 池 / 孤儿进程清理 / 探活短连接 / 运行时统计）
- [ ] 严格平滑轮询如需：用 Redis 共享状态，不引入单机 Map

## 接口契约（覆盖通用 §4）

- 本项目是 **OpenAI 兼容** 接口，不使用标准网关 `Result<T>`。
- 请求：`{model, messages}`；`model` 是别名，转发上游前改写为部署真实模型。
- 响应：OpenAI ChatCompletion 结构；`model` 回写请求别名；`usage` 等字段原样透传。
- 错误体：`{"error":{"message","type","param","code"}}`。
- **错误文案语言**：客群为中文使用者，`message` 使用中文；`type` / `param` / `code` 保持协议英文值不变（详见 STANDARDS §3.6）。
- 完整规格：`specs/001-chat-completions/`；行为基线见知识库历史文档。

## 业务/接口约束

- `POST /v1/chat/completions` 与 `POST /chat/completions`。
- 未知模型 400；未鉴权/封禁/过期 401；越权模型 403；`stream=true` 暂未实现 400（需求 1.7）。
- 虚拟 Key（1.3）：`/key/*` 仅 master；只存哈希；`models` 为空表示不限；白名单在 core 校验。
- 限流/预算（1.4）；日志计量（1.5）；fallback（1.6）；SSE（1.7）。

## 质量门禁

```bash
mvn -Pquality verify
```

Checkstyle + SpotBugs（每模块）+ ArchUnit（层依赖/无环）+ JaCoCo 聚合行覆盖率 ≥ 70%（ai-hub-tests）。当前 88 个测试。

## 配置与密钥

- 密钥只放 `src/main/resources/application-local.yml`（gitignored）或环境变量。
- 本地启动需代理：`-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897`。
- master key 占位：`sk-local-master-key`（仅本地）。

## 外部文档源（可选）

> 全仓唯一的绝对路径登记处。需求记录、验收卡一律写「见 PROJECT.md 外部文档源」，不复制路径。

- 需求文档根（知识库 2.0 活目录）：`E:\AIWorkbench\workbench\tasks\2026-11-16_ai-hub第五期规划`
- 第 5 期需求卡：`outputs\20-项目7-第5期-优化与拆分.md`；拆分设计：`outputs\15-网关治理解耦设计.md`
- 已定事项/排期：`decisions.md`；任务卡：`task.md`
- 说明：`E:\KnowledgeBase` 是知识库 1.0 冻结副本（只读，不再迭代），新需求不再引用；换设备只改本节

## 记录

- 需求记录：`ai-workflow/records/REQ-*.md`
- 归因报告：`docs/attribution/`
- 规格：`specs/`（spec-kit）；变更：`openspec/`（可选）
