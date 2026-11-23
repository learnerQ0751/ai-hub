# AI 开发工作流（可移植包）

> 复制本目录到任意项目根目录，运行 `install.ps1`，即可使用。不依赖 Vault、不依赖 Cursor/Claude Code 的专有格式。

## 每天先看（人）

| 文件 | 内容 |
|---|---|
| **`STATUS.md`** | 当前状态：需求进度 / 接口 / **数据表速查（按业务域，含用途与关系）** / 配置 / **变更矩阵** / 下一步 |
| **`DESIGN.md`** | 设计基线：唯一架构图 / 模块 / 运行时链路 / 跨需求决策 / 演进记录 |
| **`acceptance/REQ-x.y.md`** | 单个需求的验收：标准冻结 / 阅读路径 / 理解程度 / 5 题 / 亲手改口子 |

## 读者分工

- **人**：`STATUS.md` → `DESIGN.md` → `acceptance/REQ-x.y.md`
- **AI**：`docs/WORKFLOW.md` → `docs/PROJECT.md` / `docs/STANDARDS.md` → `STATUS.md` / `DESIGN.md` → 相关 `records/REQ-*.md`
- **共读**：`records/REQ-x.y.md` §1-3 人向说明 + §4 影响面

## 工具裁剪（不要全套叠加）

| 规模 | 使用 |
|---|---|
| 小改动（<半天） | 直接改 + 测试 |
| 单需求（1-3 天） | REQ + acceptance + TDD |
| 新能力/跨模块（3 天+） | + spec-kit（0→1）或 OpenSpec（存量变更）+ Superpowers |
| 架构级 | + 更新 DESIGN 与活图 |

规格以 `specs/` / `openspec/` 为真源，REQ 只引用；计划以 plan/tasks 为真源，REQ 只留摘要。

## 新项目三步

1. 把整个 `ai-workflow/` 目录复制到新项目根目录。
2. 运行：
   ```powershell
   .\ai-workflow\install.ps1 -Agent both -NewProject
   ```
   按公司环境把 `both` 换成 `claude`、`cursor` 或 `generic`。
3. 编辑 `docs/PROJECT.md`（技术栈/包名/端口/项目专属规则），然后复制 `templates/REQ.md` → `records/`、`templates/ACCEPTANCE.md` → `acceptance/` 开始第一个需求。
4. 每次收尾运行 `.\ai-workflow\scripts\status.ps1`，看 `STATUS.md`。

## 工具解耦

| 环境 | 安装命令 | 生成 |
|---|---|---|
| **JetBrains IDEA + Claude Code 插件** | `-Agent claude` | `CLAUDE.md`（插件从项目根目录读取） |
| Claude Code CLI / 其他 IDE 插件 | `-Agent claude` | `CLAUDE.md` |
| 只用 Cursor | `-Agent cursor` | `.cursor/rules/ai-workflow.mdc` |
| 两者都用 | `-Agent both` | 两个都生成 |
| 其他 agent | `-Agent generic` | `AGENTS.md` |

## 目录

```text
ai-workflow/
├── README.md                 # 本文件（入口导航）
├── STATUS.md                 # ★ 人：当前状态（脚本生成）
├── DESIGN.md                 # ★ 人：设计基线
├── acceptance/               # ★ 人：验收卡（≤30 行：标准/怎么验/三分钟看懂/限制）
│   ├── REQ-*.md
│   └── assets/               # 设计活图（design.drawio + design.svg）
├── docs/                     # 规范、流程与数据模型（AI 必读，人按需查）
│   ├── WORKFLOW.md
│   ├── STANDARDS.md
│   ├── PROJECT.md
│   ├── DATA-MODEL.md
│   └── ATTRIBUTION.md
├── records/                  # 需求档案（agent 向：意图/边界/契约/决策/坑）
├── requirements/INDEX.md     # 需求索引（状态/依赖/验收路径）
├── templates/                # REQ / ACCEPTANCE / PROJECT 模板
├── scripts/status.ps1        # 生成 STATUS
├── adapters/{claude,cursor,generic}/
└── install.ps1
```
