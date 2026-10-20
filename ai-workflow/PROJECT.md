# ai-hub 项目专属规则

> 本文件是项目专属规则；通用规则见 `STANDARDS.md`。**项目规则优先**。

## 基本信息

- 技术栈：Java 21 / Spring Boot 3.3.11 / Maven / Jackson / Jakarta Validation
- 包名：`com.qtua.aihub`
- 默认端口：`18080`（本机 8080 被其他服务占用）
- 当前进度：需求 1.1 已完成；下一步需求 1.2（模型路由）

## IDE / 运行

- 推荐 **IntelliJ IDEA**：打开 `E:\pro\ai-hub`（或 `pom.xml`）作为 Maven 项目，Project SDK 选 JDK 21。
- Claude Code 插件从项目根目录启动；读取根目录 `CLAUDE.md` → `ai-workflow/`。
- Maven 使用本机 settings.xml；终端运行 `mvn -Pquality verify`。
- `.vscode/settings.json` 是 Cursor 兼容配置，IDEA 不使用。

## 分层结构（覆盖通用 §2）

- 本项目是轻量网关，不采用 Drip 六层；分层为：
  `Controller/gateway → Service → UpstreamClient`，辅以 `config` / `filter`。
- Controller 只做 HTTP 契约与响应；Service 做校验、别名映射、编排；UpstreamClient 只做上游 HTTP。
- 本期无数据库；后续需求按 1.3-1.5 引入 PostgreSQL/Redis 时再补 DAL/限流层。

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
