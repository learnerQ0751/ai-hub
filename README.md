# ai-hub

项目 7（Agent 与 MCP 集群）Java 重实现——LLM 网关核心。

## 工作流

- **0→1（新模块）**：spec-kit（`specs/`）
- **1→100（存量迭代）**：OpenSpec（`openspec/`）+ Superpowers 技能
- **归因**：git-ai（行级 agent/model/prompt 归因）
- **规范**：`standards/` 规则卡 → 生成 constitution / CLAUDE.md / Cursor rules / lint 配置

## 本地启动

```bash
# JDK 21（系统默认 JDK8，构建前显式设置）
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"
export PATH="$JAVA_HOME/bin:$PATH"

# 配置本地密钥（gitignored）
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 质量门禁

```bash
mvn -Pquality verify
```

## 参照物

- LiteLLM 行为基线：知识库任务目录 `06-LiteLLM行为基线`
- 需求卡：`05-项目7-第1期-LLM网关核心`
