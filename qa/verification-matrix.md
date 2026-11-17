# 第五期验证矩阵（QA 草案 v0.1）

> 口径：`E:\AIWorkbench\...\21-第五期待验收标准.md` §1–§2；
> 契约：`docs/API-PATHS.md`（2026-11-17 冻结）。
> 基线：`qa/integration` @ 89f334b（= main），后端两应用已实测可起，前端 main 为骨架。
> 每次交付按本表重跑并在「实测」列追加结果。

## 0. 验证台

| 能力 | 资产 | 状态 |
|---|---|---|
| 一键启动 console(18081)+gateway(18080)+前端(5175) | `scripts/qa-start.ps1`（端口占用时校验属主，不杀别人进程） | 已跑通 |
| API 基线冒烟（健康/鉴权隔离/Key 生命周期/只读端点） | `scripts/qa-smoke.ps1` | 17/17 通过（2026-11-17） |
| 浏览器 E2E | `frontend/e2e`（Playwright，本机 Chrome channel） | 链路用例 1 条通过；应用用例 2 条因基线缺文件挂起 |
| 截图 | `frontend/e2e/artifacts/`（每次运行生成） | 已有 infra-landing.png |

## 1. 全局必过项

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| G-01 | 两应用分别启动、端口不冲突、健康检查 200 | `qa-start.ps1`；`GET /actuator/health` 两端断言 200 | 无 | 通过（18081/18080） |
| G-02 | master→gateway 403；无凭证 401；虚拟 Key→console 403；虚拟 Key→gateway 200 | `qa-smoke.ps1` 四条断言 + 错误体 type 校验 | 无 | 通过 |
| G-03 | 租户隔离：A 的 Key/文档/记忆 B 不可见 | 建 qa-tenant-a/b 两套 Key，交叉查 `/v1/admin/keys`、`/v1/rag/documents`、`/v1/memory/recall`，断言互不可见 | 需种子数据（两租户文档+记忆）；回归并入 `mvn -Pquality verify` | 待跑 |
| G-04 | 统一错误体 code/message/type，前端可读 | 对 401/403/404/409 每个场景断言 JSON 结构；页面断言错误态文案非白屏 | 无（API 侧）；页面侧待实现 | API 抽样通过 |
| G-05 | 前端接口路径只出现在 `src/api/endpoints.ts` | grep 页面/feature 下 fetch/URL 字面量；值非空即失败 | 无 | 待跑（静态检查） |
| G-06 | mock 一个开关关闭、无残留假数据 | `VITE_USE_MOCK_SESSION=false` 起前端，逐页对照请求日志；MCP 页切真实数据源 | 需控制台会话端点 + MCP 真接口接线 | 阻塞 |

## 2. 模块功能点

### 2.1 登录 / 会话（后端会话端点未实现，§7.1）

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| A-01 | 错误密码提示「用户名或密码错误」不跳转 | UI：填错密码提交，断言提示文案 + URL 停留 | console 登录端点；前端登录页接真接口 | 阻塞 |
| A-02 | 连续 5 次失败返回 429 并提示稍后再试 | API：连发 5 次错误登录断言 429；UI 断言提示 | 同上 + 限流计数 | 阻塞 |
| A-03 | 正确登录进概览，刷新会话保持 | UI：登录后 reload，断言仍在概览；`context.cookies()` 断言 cookie 存在 | 同上 | 阻塞 |
| A-04 | 退出回登录页，受保护页被挡回 | UI：点退出后访问 `/keys`，断言跳 `/login` | 同上 | 阻塞 |
| A-05 | 会话 cookie 为 httpOnly | `context.cookies()` 断言 `httpOnly=true`；`page.evaluate(document.cookie)` 断言读不到 | 同上 | 阻塞 |

### 2.2 概览

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| B-01 | Key 数/模型数/今日请求量为真实统计 | 请求 `GET /v1/admin/keys`、`/v1/admin/models`、`/v1/usage` 取真值，与页面数字逐项比对（不是只看非零） | 概览页实现；需造 2 Key + 若干请求 | 阻塞（页面占位） |
| B-02 | 加载骨架 + 失败错误态可重试 | 用 Playwright route 拦截制造 500/超时，断言骨架先出现、错误态文案、点重试后恢复 | 页面实现 | 阻塞（页面占位） |

### 2.3 Key 管理

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| C-01 | 列表展示别名/租户/状态/RPM/TPM/预算/创建时间 | `GET /v1/admin/keys?tenant_id=qa-*` 断言字段齐全，页面逐列比对 | **§7.2 字段缺口：alias/metadata/rpm_limit/tpm_limit/max_budget/budget_duration 未实现** | 阻塞 |
| C-02 | 按状态、按模型筛选生效 | API：筛选前后集合断言；UI：选择筛选器后断言请求参数与行数 | 「按状态/模型筛选」属 §7.2 未实现 | 阻塞 |
| C-03 | 封禁即时变 blocked，解封恢复 | `qa-smoke.ps1` 已覆盖 API 侧（封禁→gateway 401→解封→200）；UI 点击后断言列表状态徽标 | 页面接线 | API 通过 / UI 阻塞 |
| C-04 | 新建 Key 明文仅展示一次，列表随后出现 | UI：创建后断言明文弹层；关闭后列表出现该 Key 且不再显示明文；API：`/key/generate` 响应含 key、列表不含明文 | 页面接线 | API 通过 / UI 阻塞 |
| C-05 | 分页翻页不重不漏 | 造 55 条 Key（limit=50），断言两页 key_hash 并集=全集、交集为空；UI 翻页比对 | 需种子数据（55 Key） | 待跑 |

### 2.4 模型管理

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| D-01 | 列表展示别名/描述/价格/更新时间 | `GET /v1/admin/models` 断言字段；页面逐列比对 | **§7.2 字段缺口：description/价格/updated_at 未实现** | 阻塞 |
| D-02 | 新建/编辑/删除生效 | API：POST 临时 alias → GET 可见 → PUT 改 description → GET 生效 → DELETE → 列表消失；UI 同链路 | 页面接线；测试数据用 `qa-tmp-*` 前缀并清理 | API 可跑 / UI 阻塞 |
| D-03 | 重复创建同名返回 409 + 可读提示 | API：同名再 POST 断言 409 conflict_error；UI 断言提示文案 | 同上 | API 可跑 / UI 阻塞 |

### 2.5 MCP Servers

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| E-01 | 列表/注册/注销/重探活走真接口 | UI 操作后断言对应请求 `GET/POST/DELETE /v1/mcp/servers*` 与响应码 | 前端当前走 `features/mcp/mock.ts`，需接真接口 | 阻塞（mock） |
| E-02 | 重探活后状态正确刷新 | 注册一个可控 MCP server（本地 8001），DOWN→UP→停服→reprobe 回 DOWN；断言页面徽标 | 需可控 MCP 种子服务（现 `mock_http` 指向 8001 已 DOWN） | 待跑 |
| E-03 | 空/错/加载三态，1440/768/375 三档 | Playwright 三个 viewport + route 拦截制造空/错；截图三档 | 页面已有三态（mock），接真接口后重测 | 部分可跑 |

### 2.6 Prompt

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| F-01 | 列表/新建/新增版本/发布生效 | API：POST `/v1/prompts` → POST versions → POST publish → GET 校验 | 页面占位；tenant_id 参数属 §7.2 未实现 | API 可跑 / UI 阻塞 |
| F-02 | 发布后可立即 render 出新版本 | gateway `POST /v1/prompts/{name}/render` 断言渲染内容=新版本模板 | 需虚拟 Key（已有生成脚本） | 待跑 |
| F-03 | 版本历史倒序可查看 | `GET /v1/prompts/{name}/versions` 断言顺序与内容 | 页面占位 | API 可跑 / UI 阻塞 |

### 2.7 RAG

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| H-01 | 上传文档成功、列表出现、可删除 | multipart POST `/v1/rag/documents`（file+project_id）→ GET 列表含 doc → DELETE → 消失 | 需 **DASHSCOPE_API_KEY**（embedding）；pgvector 已就绪 | 缺凭据 |
| H-02 | 检索返回 match_type/vector_score/keyword_score/rrf_score | POST `/v1/rag/search` 断言四字段存在且类型正确；both/vector/keyword 三类命中各造一例 | 同上 | 缺凭据 |
| H-03 | 关闭 hybrid 回到三期行为 | 请求 hybrid=false，断言 rrf_score=null、字段行为符合冻结契约；回归跑 `mvn -Pquality verify` 的 RAG 集成测试 | 集成测试可先行（H2+memory vector） | 待跑 |
| H-04 | answer 引用编号与内容对应，邻居不进 citations | 造同文档多切片种子，POST `/v1/rag/answer` 断言 citations 只含原命中 chunk_id、上下文含邻居标记 | DASHSCOPE_API_KEY + chat 上游 key（或本地 mock 上游） | 缺凭据 |

### 2.8 日志 / 审计 / 用量

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| I-01 | 日志按时间/状态/模型筛选 | GET `/v1/admin/logs` 造不同状态与模型的请求后，按参数筛选断言子集正确 | 造日志需真实请求（可用错误模型产生 4xx 日志，无需上游 key） | 可跑 |
| I-02 | 审计含封禁/建模型/授权 | GET `/v1/audit-logs` 断言 KEY_BLOCK/MODEL_* /PROJECT_AUTHORIZE 记录 | `qa-smoke.ps1` 已产生 KEY_BLOCK；其它待造 | 部分可跑 |
| I-03 | 用量按天聚合、与日志条数一致 | GET `/v1/usage` 的 requests 与 `/v1/admin/logs` 同窗口 count 对照 | 需同一批请求 | 待跑 |
| I-04 | 导出 CSV 可下载且非空 | GET `/v1/usage/export` 断言 200、Content-Type、行数>0 | 需有请求数据 | 待跑 |

### 2.9 Playground

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| J-01 | 选模型发消息流式返回 | UI 输入后断言 SSE 增量渲染；网络断言 `/v1/chat/completions` stream=true | **DEEPSEEK_API_KEY**（或本地 mock 上游） | 缺凭据 |
| J-02 | 上游失败显示可读错误不卡死 | 用错误模型名/断网制造失败，断言错误态文案与可重试 | 页面接线 | 缺凭据 |
| J-03 | 多轮上下文保持 | 两轮追问，断言第二次请求 messages 含首轮内容 | 页面接线 + 上游 key | 缺凭据 |

### 2.10 系统 / 健康检查

| 编号 | 功能点 | 怎么测 | 依赖 / 缺口 | 实测 |
|---|---|---|---|---|
| K-01 | 两应用健康状态正确 | `GET /actuator/health` 200/UP 双端断言 | 无 | 通过 |
| K-02 | DB/Redis 不可用时降级提示不白屏 | 用错误 DB_URL / REDIS_PORT 在独立端口起实例（不破坏现有环境），访问页面断言降级文案 | 需页面实现；独立实例端口 18082 起 | 阻塞（页面占位） |

## 3. 需种子数据 / 凭据清单

| 项 | 用途 | 归属 |
|---|---|---|
| DASHSCOPE_API_KEY | RAG 文档接入与检索（embedding） | 环境变量，勿入库 |
| DEEPSEEK_API_KEY（或本地 mock 上游） | chat / answer / Playground 真实调用 | 环境变量 |
| 可控 MCP server（127.0.0.1:8001） | MCP 注册/重探活 UP/DOWN 切换 | 本地脚本 |
| 55 条 Key 的 qa 租户 | Key 分页不重不漏 | 脚本生成并清理 |
| 同文档 3+ 切片、双租户各一份 | RAG 引用/邻居/租户隔离 | 脚本生成 |
| 控制台会话端点 | 登录/会话/退出（§7.1 未实现） | 后端需求 |

## 4. 当前阻塞项（按严重度）

1. **`LogsPage.tsx` 被 `.gitignore` 规则吞掉（2026-11-17 根因订正）**：不是漏提交。
   `frontend/app/.gitignore` 第 2 行的 `logs` 规则匹配任意层级 `logs` 目录，`src/pages/logs/` 整个目录被静默忽略。
   实测：`git check-ignore -v frontend/app/src/pages/logs/LogsPage.tsx` 输出
   `frontend/app/.gitignore:2:logs`，`git ls-files frontend/app/src/pages/logs/` 为空。
   Q 已将该行改为 `/logs`；文件由小页修改后统一提交。两条 E2E 用例保持 `fixme`，等通知后再解除。

> **文件缺失排查顺序（写进每次交付检查）**：先 `git ls-files <dir>` 看是否被跟踪，再
> `git check-ignore -v <path>` 看是否被忽略规则命中，最后 `Test-Path` 看盘上是否存在；
> 三条都查完才允许下「漏提交」或「文件不存在」的结论。
2. 除 MCP 页外，其余页面均为占位组件；MCP 页数据走 mock。
3. §7.2 字段（Key alias/rpm/tpm/预算周期、模型 description/价格/更新时间）未实现，C-01/D-01/C-02 无法验收。
4. §7.1 端点（控制台会话、项目列表、切片列表、单 Key 详情、Trace 列表）未实现，A-01~A-05 无法验收。
5. 上游凭据与可控 MCP 种子服务缺失，RAG/Playground/日志造数受限。
