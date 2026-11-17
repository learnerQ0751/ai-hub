# ai-hub 前端资产地图

> **当前阶段：P0 已冻结；P1 L2 已定稿（2026-11-17）；P2 进行中（3 页接真接口）**
> 更新：2026-11-17 ｜ 工作根：`E:\repos\pro\ai-hub\frontend\`
> 本文件是前端资产的**唯一入口**，30 秒内定位任何设计/原型/源码/截图/基线。

## 0. 一句话现状

技术选型已冻结（A：React + TS + Vite + Ant Design + TanStack Query + ECharts）；首版范围已定 11 个路由页全覆盖；视觉方向已定 **D1 冷静工程台 · 纯净版**；`app/` 可 dev / build，登录已按 **httpOnly cookie 会话语义**改造。MCP Servers、虚拟 Key、模型与部署三页已出稿；**Prompt 模板、调用日志、审计日志三页已接真实 console 接口**（查询参数 + 服务端分页），其余 5 页仍是结构占位。

## 1. 现在到哪一步

| 阶段 | 产出 | 状态 | 说明 |
|---|---|---|---|
| P0 分型与选型 | `STACK.md` | **已冻结（2026-11-17）** | 应用型（CSR）；React + TS + Vite + Ant Design + TanStack Query + ECharts |
| P1 · L0 审美锚点 | `design/L0-视觉方向卡.md`、`design/style-profiles/litellm/` | **已定 D1** | D1 冷静工程台 · 纯净版；D2/D3 与补位参考保留为决策记录 |
| P1 · L1 页面地图 | `design/L1-页面地图.md` | **首版范围已确认** | 11 个路由页全覆盖；导航分组仍待确认 |
| P1 · L2 方向对决 | `board/`、`board/screens/` | **已定稿（2026-11-17 主人确认）** | MCP Servers 样板页：1440/768/375 × 正常/加载/空/错误；375 筛选器保持一行；Board 路由 `/board` |
| P1 · L3 单页精修 | `design/ui-review/` | **未创建** | 主人验收 L2 后逐轮截图归档 |
| P1 · L4 量产走查 | `design/DESIGN-BASELINE.md` | **未创建** | L3 结束后冻结 |
| P2 工程实现 | `app/` | **进行中** | 可 dev / build；登录 cookie 语义；MCP / Key / 模型为 mock；**Prompt / 调用日志 / 审计接真实接口**；其余 5 页占位 |
| P3 部署上线 | — | **未开始** | 前后端一体，Nginx 同源反代 |

## 2. 30 秒导航

| 想找什么 | 去这里 |
|---|---|
| 技术选型、候选对比、冻结声明 | `STACK.md` |
| 页面清单、每页信息块与任务路径、首版范围 | `design/L1-页面地图.md` |
| 视觉方向、保留/换掉/提升、补位参考候选 | `design/L0-视觉方向卡.md` |
| LiteLLM 的真实截图与像素取样 | `design/style-profiles/litellm/`（`profile.json` + `screens/`） |
| Design Board 怎么用、当前 L2 轮次 | `board/README.md` |
| L2 三档三态截图 | `board/screens/`（`mcp-<视口>-<状态>.png` + `board-overview.png`） |
| 登录/会话与接口路径集中表 | `app/src/api/`（`session.ts`、`endpoints.ts`、`http.ts`） |
| 接真接口的三页 | `app/src/features/{prompts,logs,audit}/` + `app/src/pages/{prompts,logs,audit}/` |
| 本地联调代理（仅 dev） | `app/vite.config.ts`：`CONSOLE_PROXY_TARGET`、`CONSOLE_PROXY_MASTER_KEY`（不提交密钥、不进生产构建） |
| 原型怎么隔离、怎么演进成生产代码 | `prototypes/README.md` |
| 生产前端（还不存在） | `app/`（P2 创建） |

## 3. 目录结构

```text
frontend/
├── README.md                        # 本文件：唯一入口
├── STACK.md                         # P0 技术选型单（已冻结）
├── DESIGN-BASELINE.md               # L3 冻结后创建
├── design/
│   ├── L0-视觉方向卡.md              # L0 已定：D1 冷静工程台 · 纯净版
│   ├── L1-页面地图.md                # L1：11 路由页全覆盖（首版范围已确认）
│   ├── style-profiles/
│   │   └── litellm/                 # 参考解剖：profile.json + 官方截图
│   ├── ui-review/                   # L3 起：每轮截图 + DECISIONS.md（未创建）
│   └── templates/                   # 页面设计单 / 验收单模板（未创建）
├── board/                           # Design Board：README + screens/（L2 三档三态截图）
├── prototypes/                      # 原型，按 L2 轮次/方案隔离（当前仅 README 空壳）
├── tools/                           # extract-style.mjs 等（L2 时创建）
└── app/                             # 生产前端脚手架（已创建，可 dev/build）
```

## 4. 铁律（与工作流真源一致）

真源（2.0 活任务目录）：`E:\AIWorkbench\workbench\tasks\2026-11-16_ai-hub第五期规划\outputs\19-前端UI收敛与VibeCoding工作流.md`

1. **分型与选型未确认，不写第一行前端代码。**
2. **UI 基线（`DESIGN-BASELINE.md`）未冻结，不写任何后端对接代码。**
3. **没有参考图/参考网站不开工**；复刻设计语言，创新信息架构，不做换皮克隆。
4. **确认即冻结，一次只改一个维度**；已冻结部分不再动。
5. **每轮确认靠 Board 链接 + 截图**，不用文字描述替代。
6. 每页必须有 **loading / empty / error** 三态；用户可见文案中文，错误提示可理解。
7. 所有前端资产只放在 `frontend/` 下；不改 `ai-hub-*` Java 代码，不把密钥/令牌写进前端。
8. 控制台会话只走后端下发且 JS 读不到的 httpOnly cookie；前端不写 localStorage / sessionStorage，不保存 token。
9. 公网部署前检查：国内可达性、HTTPS、反代规则、回滚方式。
10. **没在浏览器里实际走过主路径，不得报告"前端完成"。**
11. 任何 UI 变更同时检查**空态、错误态、加载态、窄屏（375）**四项，截图归档到 `board/screens/`。
12. 产出按 `ai-workflow/` 规范落盘；未经主人裁决不进知识库 `coreKB`。

## 4.1 技能点名（开工前必读）

技能由 `superpowers` 插件（7.0.2，项目作用域，技能名带 `superpowers:` 前缀）与 `.claude/skills/` 共同提供，不会自己运行。**每个前端任务开始前先点名本轮技能，再用 Skill 工具调用**；不点名就开工视为流程违规。

| 场景 | 技能 |
|---|---|
| 新需求 / 需求还模糊 | `superpowers:brainstorming` |
| 需求落成实现计划 | `superpowers:writing-plans` |
| 按计划逐项实现 | `superpowers:executing-plans` |
| 写功能 / 修 bug | `superpowers:test-driven-development` |
| 完成前自检、提交前 | `superpowers:verification-before-completion` |
| UI 设计 / 审查 / 打磨 / 视觉走查 | `impeccable` |
| 浏览器 E2E / 页面调试 / 冒烟 | `qi-browser-automation` |
| 翼笔易画 / 低代码（amis）页面研发 | `qi-yibiyihua-page-dev` |
| 交付前请求审查 / 接收意见 | `superpowers:requesting-code-review`、`superpowers:receiving-code-review` |
| 分支收尾 | `superpowers:finishing-a-development-branch` |
| 接手陌生模块 | `qi-rapid-onboarding` |
| 参考稿/设计稿还原 | `qi-replica-requirements` |
| 2+ 个独立任务并行 | `superpowers:dispatching-parallel-agents` |

## 5. 下一步（有顺序）

1. ~~主人拍板 `STACK.md`~~（已完成，2026-11-17 冻结）。
2. ~~确认 L1 首版范围~~（已完成：11 个路由页全覆盖）；导航分组待确认。
3. ~~选定视觉方向~~（已完成：D1 冷静工程台 · 纯净版）。
4. ~~出 MCP Servers 样板页 1440/768/375 三档三态~~（已完成并定稿 2026-11-17）。
5. L3 单页精修 → 冻结 `DESIGN-BASELINE.md` → L4 套用其余页面。
6. P2 业务实现（接口契约以 `docs/API-PATHS.md` 为准）：已完成 Prompt / 调用日志 / 审计三页；Key 与模型的 mock 数据源待后端字段就绪后切换（`USE_MOCK=false`）。
7. P3 部署；登录与会话端点已由后端补齐（契约 §4.8）。

## 6. 资产约定

- `design/`：只放设计与截图，不放生产代码。
- `prototypes/`：可运行、可独立启动，但不进生产构建；命名 `L2-<轮次>/<方案>/`。
- `app/`：唯一接真实接口的地方；原型确认后按基线演进，不直接复制原型代码。
- 截图命名：`<页面>-<视口>-<主题>.png`，例如 `mcp-list-1440-light.png`。
- 所有对外链接、截图、说明都从本文件可达；新增资产必须回填本表。
