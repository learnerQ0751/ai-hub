# ai-hub 控制台（前端生产工程）

> 当前状态：P2 脚手架。11 个路由页 + 登录页；MCP Servers 已出 L2 三档三态稿，其余 10 页仍是结构占位；没有真实接口，数据为 mock。

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
- 接口契约未冻结前只用 `src/features/*/mock.ts`；所有路径集中在 `src/api/endpoints.ts`，页面不拼 URL，也不手写响应类型。
- 会话语义已定：后端下发 httpOnly + Secure + SameSite=Lax 的会话 cookie；前端只通过 `src/api/session.ts` 探测登录态，不读/不存 token，不写 localStorage / sessionStorage。401/403 由 `src/api/http.ts` 统一清状态并回登录页。
- 登录目前是 mock（`admin / admin123`）；mock 会话只在模块内存里，刷新即回未登录。真实 cookie 会保持。
- Design Board 只在开发环境注册：`npm run dev` 后打开 `http://127.0.0.1:5173/board`。
