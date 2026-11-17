# Design Board（集中确认台）

> 当前：**L2 已完成 6 页**——MCP Servers（2026-11-17 主人确认冻结）、虚拟 Key、模型与部署、Prompt 模板、调用日志、审计日志；均为 1440 / 768 / 375 三档 × 正常 / 加载 / 空 / 错误四态。后三页接真实 console 接口。

## 它是什么

不是一个静态图墙，而是**可交互的 Figma**：同一轮的所有视觉方案以真实页面并排放在一页里，主人能 hover、填表、开弹窗、切视口、切主题，然后逐项「通过 / 打回」。

## 约定（来自工作流真源）

- 浏览器打开 `http://localhost:5173/board`。
- 版式：顶部页面切换 `[MCP Servers] [虚拟 Key] [模型与部署] [Prompt 模板] [调用日志] [审计日志]`、状态切换、三档视口并排；底部反馈区。
- 每个画板内必须是**真实可交互页面**，不是截图。
- 每个方案有「全屏」「通过」「打回」；通过记录写入 `design/ui-review/DECISIONS.md`。
- 方案隔离：`prototypes/L2-<轮次>/<方案>/`。
- Board 只在开发环境存在，不进生产构建。

## 怎么打开（L2）

```bash
cd frontend/app
npm run dev
# Board：        http://127.0.0.1:5173/board
# 单页全屏预览：  http://127.0.0.1:5173/board/preview/mcp?mode=ok
#                http://127.0.0.1:5173/board/preview/keys?mode=ok
#                http://127.0.0.1:5173/board/preview/models?mode=ok
#                http://127.0.0.1:5173/board/preview/prompts?mode=ok
#                http://127.0.0.1:5173/board/preview/logs?mode=ok
#                http://127.0.0.1:5173/board/preview/audit?mode=ok
# 状态：         mode = ok | loading | empty | error
```

- Board 路由只在 Vite 开发环境注册，生产构建不产出入口。
- 三档并排：1440（表格 + 冻结首列）、768（卡片列表）、375（单列卡片，保留查看与关键操作）。
- 页面内可切换页面与状态；iframe 内是真实可交互页面，不是静态截图。

### 接真接口的三页怎么跑

Prompt 模板 / 调用日志 / 审计日志直接请求真实 console，本地需要 console 在跑：

```bash
# 1) console（默认 18081）
# 2) 前端 dev 代理（仅开发环境；密钥只进本机进程环境，不写进代码/构建产物）
$env:CONSOLE_PROXY_TARGET = 'http://127.0.0.1:18081'
$env:CONSOLE_PROXY_MASTER_KEY = '<本地 master key>'
npm run dev
```

- `mode=ok` 是真实响应；`mode=loading|empty|error` 只是 Board 演示开关，不替换数据源，也不在客户端造假分页。
- 日志与审计的筛选、翻页全部走查询参数（`limit` / `offset` / `from` / `to` …），后端返回 `has_more` 决定下一页。

## 当前状态

- L2-1 已定稿（2026-11-17）：MCP Servers 列表 + 详情抽屉、注册抽屉、筛选、四态；三档不再改动。
- L2-2 虚拟 Key：列表（别名、租户/项目、状态、模型、限流、预算、创建时间）、状态/模型/租户筛选、封禁/解封、详情抽屉、四态。
- L2-3 模型与部署：列表（别名、描述、价格、更新时间）、租户/状态筛选、详情抽屉（fallback + 部署实例）、四态。
- L2-4 Prompt 模板（真实接口 `/v1/prompts`）：模板/租户/版本/状态/变量/创建时间列表；租户过滤；详情抽屉含版本历史、内容查看、发布与回滚。
- L2-5 调用日志（真实接口 `/v1/admin/logs`）：时间范围、模型、状态码、Key 别名（映射为 `key_id`）筛选；服务端 `limit/offset` 分页；详情抽屉。
- L2-6 审计日志（真实接口 `/v1/audit-logs`）：action / target / 时间筛选；服务端分页；before/after 差异抽屉。
- 375 档筛选器保持一行（搜索框 + 下拉），不做抽屉；选中值超长时在控件内省略，展开后下拉按内容自适应宽度。这是既定行为，不是缺陷。
- 截图在 `screens/`：
  - `mcp-<1440|768|375>-<ok|loading|empty|error>.png`
  - `keys-<1440|768|375>-<ok|loading|empty|error>.png`
  - `models-<1440|768|375>-<ok|loading|empty|error>.png`
  - `prompts-<1440|768|375>-<ok|loading|empty|error>.png`
  - `logs-<1440|768|375>-<ok|loading|empty|error>.png`
  - `audit-<1440|768|375>-<ok|loading|empty|error>.png`
  - 总览 `board-overview.png`
- 375 档截图通过 375px iframe 精确取景（Windows 无头 Chrome 会把窗口宽度钳制到 500px 左右，直接 `--window-size=375` 会按更宽布局裁图）；截图统一用 `frontend/app/scripts/capture-board.mjs` 走 CDP 设置视口（`page` 传 `board` 可更新总览）。
- MCP / Key / 模型三页仍是 mock（字段按 `docs/API-PATHS.md` §7.2 预留）；Prompt / 日志 / 审计三页的真接口字段已实测对齐（2026-11-17）。
- 下一步：L3 单页精修，结论写入 `design/ui-review/`。
