# REQ-5.5 验收：第五期总验收材料与控制台端点补齐

- 状态：待验收（2026-11-17 实现与自检完成；2026-11-18 收口复验通过，见 §5）
- 验收人：主人
- 需求记录：`../records/REQ-5.5.md`

## 1. 需求验收标准

> 实现前冻结，唯一勾选点。

- [x] 控制台会话端点（登录 / 探测 / 退出）在主线基线可用，cookie 为 httpOnly
- [x] `GET /v1/admin/keys` 返回 alias、metadata、rpm_limit、tpm_limit、max_budget、budget_duration，支持 status/model 筛选
- [x] `GET /v1/admin/models` 返回 description、输入/输出价格、created_at/updated_at，支持 tenant_id/status 筛选
- [x] `GET /v1/admin/projects` 可列出租户下的项目并分页，支持 tenant_id/status
- [x] `GET /v1/rag/documents/{docId}/chunks` 按文档分页返回切片（chunk_index 升序），越项目/不存在 404
- [x] `GET /v1/admin/keys/{keyHash}` 返回与列表一致的字段，不存在 404
- [x] `GET /v1/traces` 按 trace_id 聚合分页，支持 tenant_id/project_id/key_id/status/from/to
- [x] 四个新端点均拒绝虚拟 Key（403），不改动既有路径、字段与鉴权
- [x] 门禁通过

**验收命令**：

```bash
# 会话
curl -i -X POST localhost:18081/v1/admin/session \
  -H "Content-Type: application/json" \
  -d '{"username":"<账号>","password":"<明文>"}'
# 期望：200 + Set-Cookie: HttpOnly; SameSite=Lax; Path=/v1/admin

# 新端点（master key 或会话 cookie）
curl -s "localhost:18081/v1/admin/projects?tenant_id=default" -H "Authorization: Bearer $MASTER"
curl -s "localhost:18081/v1/rag/documents/<docId>/chunks?project_id=<项目>&limit=1" -H "Authorization: Bearer $MASTER"
curl -s "localhost:18081/v1/admin/keys/<keyHash>" -H "Authorization: Bearer $MASTER"
curl -s "localhost:18081/v1/traces?tenant_id=default&limit=5" -H "Authorization: Bearer $MASTER"
# 期望：200，items/count/has_more；未知 key_hash 与越项目 docId 返回 404

# 虚拟 Key 打这些端点
# 期望：403 authorization_error
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1 + `records/REQ-5.5.md` §1（3 分钟）
2. `docs/API-PATHS.md` §4.1 / §4.5 / §4.6 / §4.9（4 分钟）
3. 核心文件（3 分钟）：`AdminController`、`RagDocumentController`、`TraceController`、
   `ProjectMapper.xml`、`TraceEventMapper.xml#selectTracePage`

## 3. 实现要点与参考答案

- **项目列表的数据来源**：`projects` 表 + `api_keys`/`rag_documents` 实际出现过的租户+项目对。
  理由：项目表只在显式建项目时写入，而验收环境里的 Key、文档可能先于项目登记存在；
  只读项目表会导致管理台选不到项目。未登记项目的 name 回落为 project_id、status 回落为 ACTIVE。
- **切片列表的租户口径**：`TenantScopeGuard.resolveProject`，master 走请求声明的 project_id；
  文档归属校验复用 `RagDocumentCore.find(tenant, docId, project)`，不属于该项目统一 404。
- **Trace 列表的状态语义**：`status` 为该 trace 所有分段的最大状态码（最坏情况），
  列表按首个事件时间倒序；`status` 筛选作用于聚合结果而非单条事件。
- **鉴权**：四个端点全在 console，由 `AdminAuthFilter` 统一校验 master key 或会话 cookie；
  虚拟 Key 403。会话端点本身匿名可达但自行校验登录态。

## 4. 测试与证据

- `ConsoleGapEndpointsIntegrationTest`：4/4 通过（2026-11-17）。
- 回归子集 30/30 通过：ConsoleIsolationTest、ArchitectureTest、AdminIntegrationTest、
  RagDocumentIntegrationTest、TraceIntegrationTest、ConsoleSessionIntegrationTest。
- 提交：`b2f72c6`（会话与 §7.2 字段入基线）、`ae6a5a4`（四端点）。
- 未验证：前端页面调用与浏览器 E2E（小页/小验）；真实 PostgreSQL 下的 SQL 兼容性
  （集成测试用 H2 PostgreSQL 模式，SQL 只用 UNION ALL / GROUP BY / HAVING / LIMIT OFFSET，
  与生产 Postgres 语法一致，但仍需小验在共享库复跑）。

## 5. 第五期收口复验（2026-11-18）

### 5.1 环境与代码身份

- 代码：`main` = `bcacb38`（本地，未 push）；本轮新增 `93d07c8`（模型页 CRUD）、
  `bcacb38`（MCP 健康状态枚举归一），此前 `37a60d3`/`8654553`（`POST /v1/admin/keys` 与用例）。
- 三端全部由 `E:\pro\ai-hub-main` 工作树启动（`scripts/start-local.ps1`），jar 由 main 现场打包：
  前端 `http://127.0.0.1:5175`、console `18081`、gateway `18080`。
- 登录：`admin / admin123`（脚本默认，可 `-AdminPassword` 覆盖；本地 http 下 cookie 免 Secure）。

### 5.2 缺陷关闭（P1–P11）

| 编号 | 结论 | 证据 |
|---|---|---|
| P1 控制面 cookie 覆盖 | 已解除 | 控制面端点统一收口 `/v1/admin/**`：新路径全 200、旧路径带 cookie 全 401；登录后 11 页 0 个 401 |
| P2 模型重名 | 已修复 | 同名不同配置 → 409 `conflict_error` |
| P3 价格空 | 已修复 | `chat-default` 输入 0.27 / 输出 1.10；未配置显示「未配置」 |
| P4 会话形状 | 已修复 | `{username, expires_at}` 扁平解析；刷新保持登录（A-03） |
| P5 `/keys` 被代理吞 | 已修复 | 直连 5175/keys 返回 SPA HTML |
| P6 Keys/Models mock | 已修复 | `USE_MOCK=false`，真实数据 |
| P7 mock 标签 | 已修复 | 11 页徽标「真实接口」 |
| P8 首屏 401 提示 | 已修复 | probe 401/403 静默 |
| P9 新建 Key | 已修复 | 会话 200 + 一次性明文 51 位；匿名 401；列表/详情无明文 |
| P10 MCP 健康列空白 | 已修复（`bcacb38`） | 后端 `ACTIVE`/`DOWN` 归一为 `UP`/`DOWN`/`UNKNOWN`，单测覆盖 |
| P11 降级徽标残留 | 已知小缺陷 | 停 Redis 期间旧徽标短暂残留，恢复后刷新正确；不阻塞验收 |

### 5.3 用例与实测

- 小验 `qa/integration @ 8c9d762`：52 项矩阵 49 通过 / 2 部分 / 1 不通过（该 1 项即 P10，已修）；
  API/专项 157 断言 0 败；Playwright 20/21（唯一红例为 P10 断言）。
- 后端：`mvn -Pquality verify` BUILD SUCCESS，ai-hub-tests 180 用例 0 失败；集成测试 174/174。
- 前端：`npm run verify` 退出码 0，80/80 单测，生产构建通过。
- 主控复核（Q，2026-11-18）：真实登录 200；11 页面全 200 HTML；`/v1/admin/keys` 会话 200 / 匿名 401 /
  master 200；模型 CRUD 新建→列表→编辑→删除全 200 且数据一致。

### 5.4 验收命令（本地）

```bash
# 登录（拿 httpOnly cookie）
curl -i -X POST http://127.0.0.1:5175/v1/admin/session \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
# 新建 Key（会话 cookie）
curl -s -X POST http://127.0.0.1:5175/v1/admin/keys -H "Content-Type: application/json" \
  -b '<cookie>' -d '{"name":"demo"}'
# 模型别名 CRUD
curl -s -X POST http://127.0.0.1:5175/v1/admin/models -H "Content-Type: application/json" \
  -b '<cookie>' -d '{"alias":"demo","tenant_id":"platform","status":"ACTIVE","fallbacks":[],"deployments":[]}'
```

### 5.5 仍未覆盖

- Playground 非流式返回（J-01 口径待定）；公网/反代/HTTPS（部署侧，未执行）；真实上游模型调用需主人现场配 key。
