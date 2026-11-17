# REQ-5.5：第五期总验收材料与控制台端点补齐

- 日期：2026-11-17
- 状态：已完成（待主人验收）
- 关联规格（知识库 2.0）：`E:\AIWorkbench\workbench\tasks\2026-11-16_ai-hub第五期规划\outputs\20-项目7-第5期-优化与拆分.md`
- 依赖需求：REQ-5.1、REQ-5.2、REQ-5.3、REQ-5.4
- 提交：b2f72c6（会话与 §7.2 字段入基线）、ae6a5a4（§7.1 四端点）

## 1. 是什么

第五期的总验收依赖控制台能真实驱动后端。本轮补齐该验收链路上的两处缺口：

1. **控制台会话端点落主线**：`POST/GET/DELETE /v1/admin/session` 与 §7.2 字段
   （Key 列表 alias/metadata/rpm/tpm/budget、模型列表 description/价格/时间、Prompt 租户参数）
   原在 `feature/console-session-and-fields`，现摘取到以 main 为基线的
   `feature/console-phase5-endpoints`，不含该分支上的前端在制品快照。
2. **§7.1 四个端点缺口**：
   - `GET /v1/admin/projects`（项目列表）
   - `GET /v1/rag/documents/{docId}/chunks`（文档切片列表）
   - `GET /v1/admin/keys/{keyHash}`（单 Key 详情）
   - `GET /v1/traces`（Trace 列表，按 trace_id 聚合分页）

## 2. 有什么用

- 管理台可登录、刷新保持会话、退出，并可一次拿到 Key/模型的完整展示字段。
- 管理台可下拉选择租户下的项目、按文档翻页查看切片、按 key_hash 打开单个 Key 详情、
  按租户与时间筛选 Trace 列表。
- 小验的 A-01~A-05、C-01/C-02、D-01 与项目/切片/Trace 相关用例具备了可调用的后端。

## 3. 怎么实现的

- 会话与字段来自既有提交 `789f949`、`d4da254`、`ceaa826`，原样摘取并复跑集成测试。
- 四个新端点沿用现有分层：Controller → Service → Core/Manager → Mapper。
  - 项目列表在 Mapper 内合并 `projects` 表与 `api_keys`、`rag_documents` 中出现的
    租户+项目对；未登记项目以 `project_id` 为 name、`ACTIVE` 为 status。
  - 切片列表按 `tenant_id + project_id + doc_id` 过滤，`chunk_index` 升序，limit+1 判定 has_more。
  - 单 Key 详情复用列表项的 DTO 与脱敏口径。
  - Trace 列表按 `trace_id` 聚合 `MIN/MAX(created_at)`、`COUNT(1)`、`MAX(status)`，
    按首个事件倒序；`status` 筛选作用于聚合后的最大状态码。
- 四个端点均挂在 console，沿用 `AdminAuthFilter`（master key 或会话 cookie；虚拟 Key 403）。

## 4. 影响面

```yaml
modules:
  add:
  change: [ai-hub-api, ai-hub-dal, ai-hub-manager, ai-hub-core, ai-hub-service, ai-hub-console, ai-hub-tests]
interfaces:
  add:
    - GET /v1/admin/projects
    - GET /v1/rag/documents/{docId}/chunks
    - GET /v1/admin/keys/{keyHash}
    - GET /v1/traces
  change: []
  remove: []
tables:
  add: []
  change: []
config: []
depends_on: [REQ-5.1, REQ-5.2, REQ-5.3, REQ-5.4]
compat: additive
gate: mvn -Pquality verify
regression: [ConsoleGapEndpointsIntegrationTest, ConsoleIsolationTest, ArchitectureTest, AdminIntegrationTest, RagDocumentIntegrationTest, TraceIntegrationTest, ConsoleSessionIntegrationTest]
```

## 5. 验证

- 新增 `ConsoleGapEndpointsIntegrationTest`：4 用例覆盖四个端点的字段、分页、筛选与
  404/403/400 分支。
- 回归测试 30 个用例全绿（含隔离、架构、Admin、RAG 文档、Trace、会话）。
- 全量 `mvn -Pquality verify` 结果见提交说明与验收台账。

## 6. 未覆盖

- 5.6 四期补强（Prompt 跨租户 / TTFT）不在本轮范围。
- 前端页面接真接口与浏览器 E2E 由小页、小验负责。
- 部署与公网地址不在本轮范围（任务卡已定：验收前不部署）。
