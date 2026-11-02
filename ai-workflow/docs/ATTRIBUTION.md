# AI 归因（可选）

> 有 git-ai 就用；没有也不影响开发流程。归因是证据，不是门禁。

## 有 git-ai

```bash
# 当前工作区（未提交）的 AI/人工/未知行
git-ai status --json

# 当前 commit 的行级差异
git-ai diff HEAD

# 当前 commit / 区间统计
git-ai stats --json
git-ai stats <start>..<end> --json

# 文件级归因
git-ai blame <file>

# 查看 prompt（需要 session/task id）
git-ai log --raw
git-ai show-prompt <session::task-id>
```

## 两个前提

1. 安装 git-ai 后**重启 agent 会话**。
2. agent 从**仓库根目录**启动；从其他目录启动时，行级归因可能仍工作，但 prompt 为空。

## 没有 git-ai

在提交信息或 PR 描述中记录：

- 哪些部分由 AI 生成、哪些人工修改；
- 使用了哪个 agent/model；
- 关键决策与验收结果。

## 报告存放

建议放在项目的 `docs/attribution/`（或团队约定的位置），每个需求一个 JSON/Markdown。
