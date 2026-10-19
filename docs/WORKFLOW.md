# AI 开发工作流使用指南（ai-hub）

> 目标：你在 Cursor / Claude Code 里按步骤提出需求，AI 产出符合规范的代码；每次提交都有行级 AI 归因。

## 0. 一句话总览

- **0→1（新模块）**：spec-kit —— `/speckit-specify → /speckit-clarify → /speckit-plan → /speckit-tasks → /speckit-analyze → /speckit-implement`
- **1→100（存量迭代）**：OpenSpec —— `/opsx:explore → /opsx:propose → /opsx:apply → /opsx:archive`
- **规范**：`standards/rules/*.yaml` 唯一真源，生成 constitution / CLAUDE.md / Cursor rules / 硬门禁
- **归因**：git-ai 自动记录每行代码来自哪个 agent / 模型 / prompt
- **门禁**：每个任务结束跑 `mvn -Pquality verify`

## 1. 明天开始怎么用（推荐流程）

### 1.1 打开项目

```bash
cd E:\pro\ai-hub
```

- 用 **Cursor** 打开该项目。
- **Cursor 内**：spec-kit 命令已生成到 `.cursor/commands/`，直接用 `/speckit.specify`、`/speckit.plan` 等（注意是点号）；OpenSpec 用 `/opsx:*`。
- **Claude Code 内**：用 `/speckit-specify`、`/speckit-plan` 等（连字符形式）。两种写法对应同一套模板。

### 1.2 每个需求的标准动作

以「需求 1.2 模型路由」为例：

```text
1. 新建分支
   git switch -c feat/req-1.2-model-router

2. 在 Claude Code 中输入：
   /speckit-specify "实现模型配置与多部署路由：配置结构对齐 LiteLLM model_list；同名多部署 simple-shuffle；GET /models 返回别名"

3. 让 AI 依次执行：
   /speckit-clarify
   /speckit-plan
   /speckit-tasks
   /speckit-analyze

4. 审阅产物（至少看 spec.md 的验收标准、plan.md 的规范检查、tasks.md 的任务边界）

5. 执行：
   /speckit-implement

6. 验收：
   mvn -Pquality verify
   git-ai stats --json
   git-ai blame src/main/java/.../关键文件.java

7. 提交：
   git add .
   git commit -m "feat: implement model router (req 1.2)"
```

> 需求卡在知识库任务目录 `05-项目7-第1期-LLM网关核心`；把对应需求段落贴给 `/speckit-specify` 即可。

### 1.3 存量迭代（1→100）

修改已有功能 / 修 bug / 重构时用 OpenSpec：

```text
/opsx:explore "登录超时问题"
/opsx:propose "修复登录超时"
/opsx:apply
/opsx:archive
```

小改动（typo、一行修复）可直接改 + 测试，不必套仪式。

## 2. 规范如何生效

### 2.1 三层规则

| 层 | 内容 | 文件 |
|---|---|---|
| L0 个人底线 | 密钥、异常、提交、测试、门禁 | `standards/rules/L0-personal.yaml` |
| L1 通用 Java/Spring | 判空、魔法值、方法长度、分层、SQL、单测 | `standards/rules/L1-java.yaml` |
| L2 项目 7 专属 | OpenAI 兼容、错误体、Key 哈希、计价、限流、fallback、SSE | `standards/rules/L2-project7.yaml` |

### 2.2 生成物（不要手改）

- `.specify/memory/constitution.md` —— spec-kit 的项目宪法
- `CLAUDE.md` 的 `GENERATED` 区块 —— Claude Code 上下文
- `.cursor/rules/{java,sql,test,api}.mdc` —— Cursor 路径规则
- `standards/index.md` —— 人读索引

### 2.3 修改规则

```bash
# 1. 编辑 standards/rules/*.yaml
# 2. 校验
python standards/generator/validate.py
# 3. 重新生成所有消费端
python standards/generator/generate.py
# 4. 跑门禁
mvn -Pquality verify
```

## 3. 质量门禁

```bash
mvn -Pquality verify
```

包含：

- **Checkstyle**：命名、方法 ≤70 行、`== null` 禁止、`System.out` 禁止、魔法值
- **SpotBugs**：常见缺陷模式（已按类豁免 Spring 构造器注入误报）
- **ArchUnit**：包依赖无环
- **JaCoCo**：覆盖率检查（当前阈值见 `pom.xml`）
- **JUnit**：18 个单测（需求 1.1 已覆盖成功 + 关键异常）

## 4. 归因（git-ai）

### 4.1 四个问题的命令

| 问题 | 命令 |
|---|---|
| 这次 commit 哪些行来自 AI/人工/未知？ | `git-ai diff HEAD` / `git-ai stats --json` |
| 某段代码归因给哪个 agent/model/人？ | `git-ai blame <file>` |
| 一个 commit 区间的 AI 占比？ | `git-ai stats <start>..<end> --json` |
| 改动背后的 prompt？ | `git-ai show-prompt <id>` / `git-ai log --raw` |

### 4.2 当前状态

- git-ai 1.7.5 已安装，Claude Code / Cursor hooks 已配置。
- 首次提交（`00d0a9b`）在安装前，记为 `unknown`。
- 之后的改动已实测捕获 AI checkpoint（`claude deepseek-flash`）。
- 归因 notes 存在 Git Notes（`refs/notes/ai`），`git push/fetch` 会同步。

> 注意：GitHub squash merge 可能丢 notes；本仓库单人开发，建议用 merge commit 或直接提交。

### 4.3 Prompt 追溯的两个前提

1. **安装后重启 agent**：git-ai 安装时若 Claude Code / Cursor 正在运行，需要重启会话。
2. **agent 从仓库根目录启动**：在 `E:\pro\ai-hub` 下启动 Cursor，或在仓库根目录的终端运行 `claude`。如果从知识库目录启动，行级归因仍可能工作，但 prompt 文本为空（git-ai issue #871 的 cwd 行为）。

检查：

```bash
git-ai log --raw
git-ai show-prompt <session::task-id>
```

## 5. 常见问题

| 问题 | 处理 |
|---|---|
| 端口冲突 | 默认 18080；如被占用改 `server.port` |
| DeepSeek 超时 | 启动时加 `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897` |
| spec-kit 命令在 Cursor | 已通过 generic 模式生成 `.cursor/commands/speckit.*.md`；Cursor 中用 `/speckit.specify` 等；Claude Code 中用 `/speckit-specify` 等 |
| 硬门禁误报 | 先看 `target/site/checkstyle.html` / `spotbugs.html`；必要时在 `config/` 精确豁免 |
| 密钥 | 只放 `src/main/resources/application-local.yml`（gitignored）或环境变量 |

## 6. 目录速查

```
specs/           spec-kit 产物（0→1）
openspec/        OpenSpec 产物（1→100）
standards/       规则库 + 生成器
config/          Checkstyle / SpotBugs / PMD 配置
scripts/         一键校验 / 验收脚本
docs/            工作流与归因说明
src/             代码
```
