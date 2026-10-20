# ai-hub

项目 7（Agent 与 MCP 集群）Java 重实现——LLM 网关核心。

## 入口（先看这个）

- AI 工作流：[`ai-workflow/README.md`](ai-workflow/README.md)
- 开发规范：[`ai-workflow/STANDARDS.md`](ai-workflow/STANDARDS.md) + [`ai-workflow/PROJECT.md`](ai-workflow/PROJECT.md)
- 开发流程：[`ai-workflow/WORKFLOW.md`](ai-workflow/WORKFLOW.md)
- 需求记录：[`ai-workflow/records/`](ai-workflow/records/)
- 需求 1.1 规格：[`specs/001-chat-completions/`](specs/001-chat-completions/)
- AI 归因报告：[`docs/attribution/`](docs/attribution/)

## 当前进度

需求 1.1（统一入口 + 单模型转发）已完成并通过真实验收；下一步需求 1.2（模型路由）。

## 本地启动

```bash
# JDK 21（系统默认 JDK8，构建前显式设置）
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"
export PATH="$JAVA_HOME/bin:$PATH"

# 配置本地密钥（gitignored）
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

mvn spring-boot:run -Dspring-boot.run.profiles=local
```

默认端口 `18080`（避免与本机 8080 上的其他服务冲突）。

## 质量门禁

```bash
mvn -Pquality verify
```
