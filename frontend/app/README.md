# ai-hub 控制台（前端生产工程）

> 当前状态：P2 脚手架 + L2 三页。11 个路由页 + 登录页；MCP Servers、虚拟 Key、模型与部署已出 L2 三档四态稿，其余 8 页仍是结构占位；没有真实接口，数据为 mock。

## 命令

```bash
npm install
npm run dev        # http://127.0.0.1:5173
npm run build      # tsc -b + vite build
npm run lint
npm run format
npm run typecheck
npm run test
npm run verify     # lint + format:check + typecheck + test + build
```

## 约定

- 技术栈：React + TypeScript + Vite + Ant Design + TanStack Query + ECharts。
- 路径别名：`@/` → `src/`。
- 视觉 token 在 `src/styles/theme.ts`（D1 冷静工程台 · 纯净版）。
- 接口路径集中在 `src/api/endpoints.ts`，口径以 `docs/API-PATHS.md`（2026-11-17 冻结）为准；页面不拼 URL，也不手写响应类型。
- 契约未实现的字段与端点不登记（Trace 列表、文档切片列表、项目列表），见 `endpoints.ts` 注释。
- 数据层：`src/features/<domain>/api.ts` 是唯一出口；`USE_MOCK = true` 时由 fixtures 顶替数据源，mock 返回结构与真实响应一致，切到真实接口只改该常量。
- 会话语义已定：后端下发 httpOnly + Secure + SameSite=Lax 的会话 cookie；前端只通过 `src/api/session.ts` 探测登录态，不读/不存 token，不写 localStorage / sessionStorage。401/403 由 `src/api/http.ts` 统一清状态并回登录页。
- 登录目前是 mock（`admin / admin123`）；mock 会话只在模块内存里，刷新即回未登录。真实 cookie 会保持。
- Design Board 只在开发环境注册：`npm run dev` 后打开 `http://127.0.0.1:5173/board`。
- 截图脚本：`node scripts/capture-board.mjs <baseUrl> <outDir>`（需本机 Chrome；见 `frontend/board/README.md`）。

## 已出稿页面

| 页面        | 路由           | 数据层            | 状态      |
| ----------- | -------------- | ----------------- | --------- |
| MCP Servers | `/mcp-servers` | `features/mcp`    | L2 冻结   |
| 虚拟 Key    | `/keys`        | `features/keys`   | L2 待确认 |
| 模型与部署  | `/models`      | `features/models` | L2 待确认 |
