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

需求 1.1 已完成；需求 1.2（模型配置 + 多部署路由）代码完成，待理解验收；下一步需求 1.3（虚拟 Key）。
技术栈已与 `E:\learing_demo\ai-coder-help` 对齐：Spring Boot 4.1.1 + LangChain4j 1.22.0 + Redis/Lettuce。

## 在 IDEA 中开发

- 用 IntelliJ IDEA 打开 `E:\pro\ai-hub`（或 `pom.xml`）作为 Maven 项目；Project SDK 选 JDK 21。
- 安装 Claude Code 插件，从项目根目录启动；它会读取根目录 `CLAUDE.md` → `ai-workflow/`。
- Maven 使用本机 `D:\developtools\maven\apache-maven-3.6.3\conf\settings.xml`。
- 终端运行 `mvn -Pquality verify`。
- 不需要 Cursor 的 `.cursor/rules`；它是跨 IDE 兼容层，IDEA 可忽略。

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
