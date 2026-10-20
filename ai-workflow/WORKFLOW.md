# 开发流程（轻量版）

> 原则：按需使用仪式；小改动直接做，大改动走规格。工具可选，产物必须落仓库。

## 0. 开始前

1. 读 `STANDARDS.md`（通用）+ `PROJECT.md`（项目专属）。
2. 新需求：复制 `templates/REQ.md` → `records/REQ-<编号>.md`，填写需求与验收标准。

## 1. 规格（按需）

- **复杂需求 / 0→1**：写 `specs/NNN-<feature>/spec.md`（用户故事 + 验收）→ `plan.md` → `tasks.md`。
  - 环境允许时可用 spec-kit：`/speckit-specify → /speckit-clarify → /speckit-plan → /speckit-tasks → /speckit-implement`。
  - 不允许时按模板手写，流程不变。
- **存量迭代 / 1→100**：可用 OpenSpec：`/opsx:explore → /opsx:propose → /opsx:apply → /opsx:archive`；也可直接在 `records/REQ-*.md` 写变更点。
- **小改动（typo、一行修复）**：跳过规格，直接实现 + 测试，在记录里写一句变更说明。

## 2. 实现

- TDD：失败测试 → 最小实现 → 测试通过 → 重构。
- 遵守 `STANDARDS.md` + `PROJECT.md`。
- 外部依赖不确定时先查文档/源码，不猜 API。

## 3. 验证

- 跑项目质量门禁（见 `PROJECT.md`）。
- 跑真实链路/验收场景（接口用 curl 或测试客户端）。
- 有 git-ai 时记录归因（见 `ATTRIBUTION.md`）；没有则跳过。

## 4. 收尾

- 更新 `records/REQ-<编号>.md`：决策、验收结果、执行日志、复盘。
- 代码 + 记录一起提交：`feat: ...` / `fix: ...`。
- 提交前检查 `git status`，不提交密钥/本地配置。

## 5. Agent 适配

| Agent | 入口文件 | 说明 |
|---|---|---|
| Claude Code | `CLAUDE.md` | 由 `install.ps1 -Agent claude` 生成/追加 |
| Cursor | `.cursor/rules/ai-workflow.mdc` | 由 `install.ps1 -Agent cursor` 生成 |
| 其他 | `AGENTS.md` | 由 `install.ps1 -Agent generic` 生成 |

所有入口只指向本目录的核心文件，不复制规范内容。

## 6. 可选工具

| 工具 | 用途 | 没有时怎么办 |
|---|---|---|
| spec-kit | 规格驱动（0→1） | 手写 `spec.md/plan.md/tasks.md` |
| OpenSpec | 存量变更（1→100） | 在 `records/REQ-*.md` 写变更点 |
| git-ai | 行级 AI 归因 | 用 commit/PR 描述记录 AI 参与方式 |

## 7. 提交清单

- [ ] 测试通过，质量门禁通过
- [ ] 验收场景已验证
- [ ] `records/REQ-*.md` 已更新
- [ ] `git status` 无密钥/本地配置
- [ ] 提交信息符合规范
