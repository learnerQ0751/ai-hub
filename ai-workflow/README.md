# AI 开发工作流（可移植包）

> 复制本目录到任意项目根目录，运行 `install.ps1`，即可使用。不依赖 Vault、不依赖 Cursor/Claude Code 的专有格式。

## 一眼看

| 你要找的 | 位置 |
|---|---|
| 开发规范 | `STANDARDS.md`（通用）+ `PROJECT.md`（项目专属） |
| 开发流程 | `WORKFLOW.md` |
| 需求/决策/验收/复盘 | `records/REQ-*.md`（从 `templates/REQ.md` 复制） |
| 项目完成态 | `STATUS.md`（需求/接口/测试/下一步） |
| 更新看板 | `scripts/status.ps1` |
| Agent 接入 | `install.ps1 -Agent claude\|cursor\|both\|generic` |
| 归因（可选） | `ATTRIBUTION.md` |

## 新项目三步

1. 把整个 `ai-workflow/` 目录复制到新项目根目录。
2. 运行：
   ```powershell
   .\ai-workflow\install.ps1 -Agent both -NewProject
   ```
   按公司环境把 `both` 换成 `claude`、`cursor` 或 `generic`。
3. 编辑 `PROJECT.md`（技术栈/包名/端口/项目专属规则），然后复制 `templates/REQ.md` → `records/REQ-xxx.md` 开始第一个需求。
4. 每次收尾运行 `.\ai-workflow\scripts\status.ps1`，看 `STATUS.md`。

## 工具解耦

核心是纯 Markdown；适配层只是“指路牌”：

| 环境 | 安装命令 | 生成 |
|---|---|---|
| 只允许 Claude Code | `-Agent claude` | `CLAUDE.md` |
| 只用 Cursor | `-Agent cursor` | `.cursor/rules/ai-workflow.mdc` |
| 两者都用 | `-Agent both` | 两个都生成 |
| 其他 agent | `-Agent generic` | `AGENTS.md` |

换公司/换工具只换适配层，不改规范。

## 可选工具

- spec-kit / OpenSpec：用于规格驱动；没有也能按 `WORKFLOW.md` 手写 spec/plan/tasks。
- git-ai：用于行级 AI 归因；没有也能用 commit/PR 记录。

## 目录

```
ai-workflow/
├── README.md
├── STANDARDS.md
├── PROJECT.md
├── WORKFLOW.md
├── ATTRIBUTION.md
├── templates/REQ.md
├── templates/PROJECT.template.md
├── records/REQ-*.md
├── STATUS.md                     # 一页看板（脚本生成）
├── scripts/status.ps1            # 扫描需求/接口/测试，更新看板
├── adapters/{claude,cursor,generic}/
├── install.ps1
└── REFERENCE/legacy-standards/   # 历史全量规则库，默认不加载
```
