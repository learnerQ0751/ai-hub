# 项目状态

<!-- STATUS:GENERATED:START -->
> 本区块由 `ai-workflow/scripts/status.ps1` 生成；手工备注写在下方。

**最后更新**：2026-10-20 14:31

## 需求进度

| REQ | 名称 | 状态 | 验收 | 接口 | 提交 |
|---|---|---|---|---|---|
| REQ-1.1 | 统一入口 + 单模型转发（非流式） | 已完成 | 6/6 | POST /v1/chat/completions, POST /chat/completions | 60777b0 |
| REQ-1.2 | 模型配置 + 多部署路由 | 已完成 | 6/6 | GET /models, GET /v1/models | —（待提交） |

## 接口清单（来自 Controller）

| 方法 | 路径 | Handler |
|---|---|---|
| POST | /chat/completions | ChatCompletionController#chat |
| GET | /models | ModelController#listModels |
| POST | /v1/chat/completions | ChatCompletionController#chat |
| GET | /v1/models | ModelController#listModels |

## 测试

- 41 个 @Test / 11 个测试类

## 怎么演示

- 按 `records/REQ-*.md` 的验收标准逐项验证。
- 项目有 `scripts/acceptance.ps1` 时可直接运行。

## 下一步

- 暂无进行中的需求。

<!-- STATUS:GENERATED:END -->

## 手工备注

<!-- 可手动补充；脚本只替换上方生成区块 -->