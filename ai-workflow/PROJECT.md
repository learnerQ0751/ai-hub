# ai-hub 项目专属规则

> 本文件是项目专属规则；通用规则见 `STANDARDS.md`。

## 基本信息

- 技术栈：Java 21 / Spring Boot 3.3.11 / Maven
- 包名：`com.qtua.aihub`
- 默认端口：`18080`（本机 8080 被其他服务占用）
- 当前进度：需求 1.1 已完成；下一步需求 1.2（模型路由）

## 业务/接口约束

- OpenAI 兼容：`POST /v1/chat/completions` 与 `POST /chat/completions`。
- 请求 `model` 是别名；转发上游前改写为部署的真实模型名。
- 响应 `model` 回写为请求别名（对齐 LiteLLM 正常路径）。
- 响应 JSON 原样透传，`usage` 等字段不得丢失。
- 错误体：`{"error":{"message","type","param","code"}}`；未知模型 400，未鉴权 401，流式暂未实现 400。
- 虚拟 Key 只存哈希（需求 1.3）；限流/预算（1.4）；日志计量（1.5）；fallback（1.6）；SSE（1.7）。

## 质量门禁

```bash
mvn -Pquality verify
```

包含 Checkstyle、SpotBugs、ArchUnit、JaCoCo（行覆盖率 ≥ 70%）。

## 配置与密钥

- 密钥只放 `src/main/resources/application-local.yml`（gitignored）或环境变量。
- 本地启动需代理：`-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897`。
- master key 占位：`sk-local-master-key`（仅本地）。

## 记录

- 需求记录：`ai-workflow/records/REQ-*.md`
- 归因报告：`docs/attribution/`
- 规格：`specs/`（spec-kit）；变更：`openspec/`（可选）
