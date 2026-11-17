# 技术选型单（已冻结）

> 项目：ai-hub 管理控制台 + 公网演示（求职作品集）
> 阶段：P0 · 2026-11-17
> **状态：已冻结（2026-11-17，主人确认方案 A）。技术栈变更需单独立项，不随需求顺手更换。**
> 视觉方向同步确认：**D1 冷静工程台 · 纯净版**（详见 `design/L0-视觉方向卡.md`）。

## 1. 项目分型结论

**应用型（Application）→ CSR 单页应用。**

判断依据（按工作流 P0.1 三问）：

| 问题 | 本项目 |
|---|---|
| 需要被搜索引擎收录、公网陌生人直接访问内容吗？ | **否**。控制台登录后使用；公网演示也是“登录后的应用”，不是内容站 |
| 登录后使用、重表单/表格/交互/权限吗？ | **是**。Key、模型部署、MCP、Prompt、RAG 文档、日志、用量、审计，全部是表格 + 表单 + 筛选 + 状态操作 |
| 两者都有？ | 主体是应用型。若后续要给作品集做独立介绍页，另拆一个静态入口，不塞进控制台 |

补充判断：

- 产品形态对标 LiteLLM 管理台：信息架构价值高，SEO 价值为零。
- 前后端一起部署，Nginx 同源反代，前端是纯静态产物；不需要 Node 运行时。
- 公网作品集的“可访问”指能登录体验，不是被搜索引擎索引。

> 结论：**Vite + React 的 CSR 单页**，不做 SSR/SSG。若主人要求“官网 + 控制台”一体，再按混合型分入口处理（静态介绍页 + SPA），不改变本控制台的技术选型。

## 2. 候选方案对比

评分 1–5，5 最好。“AI 生成质量”按工作流 P0.2 的语料丰富度判断。

| 维度 | A. React + Vite + Ant Design | B. Vue 3 + Vite + Element Plus | C. Next.js + React + shadcn/ui |
|---|---|---|---|
| 渲染模式 | CSR | CSR | SSR/SSG/CSR 皆可 |
| 管理台组件覆盖 | **5** 表格/表单/抽屉/筛选/空态/分页齐全 | 4 组件齐，但复杂表格生态略弱 | 3 需自建或拼装，表格/筛选要自己搭 |
| AI 生成质量 | **5** 训练语料最多 | 4 | 4 |
| 信息密度与开箱效率 | **5** 默认就适合后台 | 4 | 2 默认留白大，需重做密度 |
| 视觉上限 | 3 默认为“标准后台”，需 token 定制 | 3 同上 | **5** 高度可定制 |
| 定制成本 | 低（主题 token + 少量自研） | 低 | **高**（几乎每个组件都要自己写） |
| 生态与长期维护 | **5** | 4 | 4 但版本演进快 |
| 部署复杂度 | **5** 静态产物 | 5 | 3 需要 Node 运行时或静态导出约束 |
| 与工作流默认基线一致 | **是** | 否 | 否 |
| 适合“3 个月做出可信作品集” | **是** | 是 | 否（视觉上限高但工期不可控） |

## 3. 选定方案（A）

**React + TypeScript + Vite + Ant Design + TanStack Query + ECharts。**（2026-11-17 主人确认）

理由，按重要性排序：

1. **这是管理台，不是品牌官网。** 表格、筛选、表单、抽屉、分页、空态是主战场，Ant Design 的开箱覆盖能直接把工期花在信息架构与视觉打磨上，而不是重造表格。
2. **AI 主力生成时 React 语料最丰富**，返工率最低；工作流默认基线也是它。
3. **静态构建 + Nginx 同源反代**，与“前后端一起上公网”的部署方式最匹配，零 CORS、零 Node 运行时。
4. **视觉短板可解**：用 Ant Design 的 token 系统 + 自定义外壳 + 设计基线约束，而不是用它默认的蓝白后台。L2/L3 的验收标准会明确禁止“默认 AntD 味”。
5. **接口契约可生成**：后端出 OpenAPI 后用 `openapi-typescript` + `openapi-fetch` 生成类型与客户端，满足“禁止手写响应类型”。

### 3.1 具体技术栈

| 层 | 选择 | 已装版本 | 说明 |
|---|---|---|---|
| 语言 | TypeScript（`strict: true`） | 5.9.3 | 不用 `any` 兜底 |
| 构建 | Vite | 8.3.8 | 脚手架已跑通 dev/build |
| UI | Ant Design + ConfigProvider 自定义 token | 6.7.1 | 不 fork 组件库，不做自研组件库 |
| 图标 | `@ant-design/icons` | 6.3.4 | 与 AntD 同源 |
| 路由 | React Router（data router） | 7.18.5 | 路由级懒加载 |
| 服务端状态 | TanStack Query | 5.107.1 | 列表/详情/轮询统一缓存与失效 |
| 客户端状态 | 页面局部状态优先；仅会话/租户/偏好用轻量 store | — | 当前用 React Context 管 mock 会话，未引入 Zustand |
| 请求层 | `openapi-fetch` + 生成的 TS 类型 | 待 P2 契约冻结后安装 | 禁止手写响应类型 |
| 图表 | Apache ECharts | 6.1.0 | 已封装 `src/components/Chart.tsx` |
| 表单 | Ant Design Form | 随 AntD | 与组件库一致，减少自研校验层 |
| 日期 | dayjs | 随 AntD | 不引入第二套日期库 |
| 测试 | Vitest + Testing Library + jsdom | 5.1.0 / 16.3.3 / 29.1.1 | 已覆盖 11 路由与登录跳转 16 条用例 |
| E2E | Playwright | 待 P2 安装 | 登录、核心列表 CRUD、关键操作三条主链 |
| 代码规范 | ESLint + Prettier | 10.15.0 / 3.10.1 | flat config；已移除模板自带的 oxlint |
| 部署 | `vite build` 静态产物 + Nginx 同源反代 | — | 路径以冻结的 OpenAPI / 路径表为准 |

**与模板默认值的取舍**：`create-vite` 默认装 TypeScript 7.0.2 与 oxlint；`typescript-eslint@8.73.0` 的 peer 要求 TypeScript `<6.1.0`，因此降到 **TypeScript 5.9.3**，并改用 ESLint。该取舍不影响 React/Vite 运行，构建与测试均已通过。

### 3.2 目录约定（已创建脚手架）

```text
app/
├── src/
│   ├── api/          # queryClient、mockAuth；契约冻结后放生成的 client
│   ├── components/   # PageHeader、PagePlaceholder、Chart、RequireAuth
│   ├── features/     # 按业务域；当前只有 mcp 的类型、fixtures、mock、测试
│   ├── pages/        # 11 个路由页 + 登录
│   ├── layouts/      # AppShell（左侧纵向导航 + 顶栏）
│   ├── stores/       # 会话 Context（mock）
│   ├── styles/       # D1 token、全局样式
│   ├── router/       # 路由表 + 路由冒烟测试
│   └── test/         # jsdom 测试 setup
```

### 3.3 质量门禁

```bash
npm run verify   # lint + format:check + tsc -b + Vitest + vite build
npm run e2e      # 待 P2 安装 Playwright 后启用
```

- 2026-11-17 实测：`npm run verify` 退出码 0；16 条 Vitest 用例通过（11 路由标题 + 登录成功跳转 + 未登录拦截 + MCP 筛选 3 条）。
- 每个页面必须有 loading / empty / error 三态。
- 用户可见文案中文；错误提示必须能指导下一步，不直接抛后端英文原文。
- 列表页必须支持 URL 同步筛选条件（可分享、可回退）。

## 4. 被排除方案与原因

| 方案 | 排除原因 |
|---|---|
| Next.js / Nuxt | 本项目无 SEO 与首屏内容需求；引入 Node 运行时、SSR 缓存与部署复杂度，收益为零。若未来做官网，单独静态入口即可 |
| Vue 3 + Element Plus | 可行，但不满足“优先延续默认基线”；AI 生成语料与管理台复杂表格生态弱于 React + AntD |
| shadcn/ui 作为整套 UI | 视觉上限高，但表格、筛选、抽屉、分页都要自己搭；在“作品集工期”约束下风险大于收益。可作为局部样式灵感，不作为基础组件库 |
| MUI | 管理台专用组件不如 AntD，默认视觉偏 Material，改造成本高 |
| 低代码/在线生成（v0、Lovable） | 与本地工程脱节，无法满足设计基线冻结与可维护性 |
| 自研组件库 / 微前端 | 明确超出本项目规模，YAGNI |
| Figma / Penpot 作为确认载体 | 不能由 AI 写入画板；改用自建 Design Board |

## 5. 接口与安全约束

- **契约来源**：以后端 OpenAPI 或冻结的路径表为准；生成 TS 类型与 API client，**禁止手写响应类型**。
- 第五期正在把后端拆成 gateway（数据面，18080）与 console（控制面，18081）；管理端接口在 console，Playground/调试类调用走 gateway。**管理面路径保持 4.4 契约不动，数据面/控制面靠凭证物理隔离，不靠 URL 前缀重排。**
- **密钥不落前端**：不把 master key 写进构建产物、localStorage 或 URL；控制台登录方向已定为后端补短期会话 / 管理员账号，契约冻结前前端只用 mock（`admin / admin123`）。
- 公网演示不能暴露真实上游 api-key；演示账号应只读或使用受限虚拟 Key。

## 6. 主要风险与应对

| 风险 | 应对 |
|---|---|
| Ant Design 默认视觉不够作品集 | L0 定方向、L3 用 token + 自定义外壳压掉默认味；禁止默认蓝、默认圆角、默认阴影 |
| 组件库体积 | 路由级代码分割已启用；当前 AntD 共享 chunk 约 502KB（gzip 163KB），P2 再按 rolldown 的 code splitting 拆包 |
| 接口契约尚未冻结 | P0/P1 不接后端；P2 以冻结契约为准，先生成再写页面。管理面路径保持 4.4 契约 |
| 控制台登录/会话端点待后端实现 | 已定方向：短期会话 / 管理员账号；前端先用 mock，不自行发明鉴权 |
| 作品集演示需要“可看但不危险” | 设计只读演示模式；真实写操作仅本地/管理环境开放 |

## 7. 冻结声明

> 本单已于 2026-11-17 由主人确认并冻结。技术栈（框架/构建/语言/UI 库/状态/请求/测试）的变更需**单独立项**，不随需求顺手更换。替换必须同步更新本文件与质量门禁。

- [x] 主人确认分型结论：应用型 / CSR
- [x] 主人确认方案 A：React + TypeScript + Vite + Ant Design + TanStack Query + ECharts
- [x] 主人确认 UI 库与图表库：Ant Design + ECharts
- [x] 主人确认视觉方向：D1 冷静工程台 · 纯净版
- [x] 冻结日期：2026-11-17
- [x] 方案 B / C 保留为决策记录，不再并列评估
