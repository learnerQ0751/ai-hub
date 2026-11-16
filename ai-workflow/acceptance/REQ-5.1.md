# REQ-5.1 验收：数据面 / 控制面拆分

- 状态：待验收（2026-11-16，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-5.1.md`

## 1. 需求验收标准

> 实现前冻结，唯一勾选点。

- [x] 两个 jar 单独启动，各自 `/actuator/health` 正常
- [x] 停掉 console，数据面仍可正常转发（gateway-only 集成测试覆盖 chat/embeddings/RAG；
      手工双进程实测停 console 后 gateway health 与 `/v1/models` 均 200）
- [x] 虚拟 Key 打 console 管理端点返回 403；admin 凭证打数据面返回 403（双向实测）
- [x] 两应用同库，迁移只跑一次；重启 console 显示 up to date、历史行数不变
- [x] ArchUnit 两条边界规则生效，全量门禁通过
- [x] 集成测试按应用拆分启动，原有 4.x 用例不减少（全仓 490 → 519）

**验收命令**：

```bash
# 先 console（执行迁移），再 gateway
java -jar ai-hub-console/target/ai-hub-console-0.0.1-SNAPSHOT-exec.jar
java -jar ai-hub-gateway/target/ai-hub-gateway-0.0.1-SNAPSHOT-exec.jar

curl -s localhost:18081/actuator/health -H "Authorization: Bearer $MASTER"
curl -s localhost:18080/actuator/health
curl -s -o /dev/null -w "%{http_code}\n" localhost:18080/v1/models \
  -H "Authorization: Bearer $MASTER"          # 403
curl -s -o /dev/null -w "%{http_code}\n" "localhost:18081/v1/admin/keys?tenant_id=t" \
  -H "Authorization: Bearer $VIRTUAL"         # 403
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1 + §3.1（3 分钟）
2. `records/REQ-5.1.md` §1-5，特别是 §4.1 偏差（3 分钟）
3. 核心文件（4 分钟）：`GatewayApplication`、`ConsoleApplication`、`AuthFilter`、
   `AdminAuthFilter`、`GatewayIsolationTest`、`ConsoleIsolationTest`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **扫描范围显式化**：两个启动类不扫根包；gateway 正则排除 `service.admin`，
   console 只扫 `service.shared` + `service.admin` + 管理所需 integration/core.rag。
2. **鉴权不共用**：gateway 的 `AuthFilter` 遇 master key 直接 403；
   console 的 `AdminAuthFilter` 遇虚拟 Key 直接 403；两套 Filter 互不加载。
3. **Flyway 单归属**：console 开、gateway 关；部署顺序 console healthy → gateway。

### 3.2 自测题与参考答案

1. 为什么不能两个应用都开 Flyway？
2. 为什么管理端点路径不改成 `/v1/admin/**`？
3. 为什么 `RagDocumentService` 在 shared 而不是 admin？
4. 停掉 console 后，gateway 的聊天为什么还能工作？
5. 为什么测试不让一个类同时启动两个应用？

### 参考答案

1. 两端同时迁移会竞争 `flyway_schema_history` 与 DDL 锁；迁移是全局单写者。
2. 4.4 契约冻结；路径变更会破坏前端与验收基线。隔离靠凭证，不靠前缀。
3. 数据面 MCP 工具 `ingest_document` 复用该实现；HTTP 接入端点仍只在 console。
4. gateway 只在请求路径读 DB/本地缓存，不回调 console；Key/配置均直接读库。
5. 会翻倍上下文与启动时间，且掩盖隔离缺陷；跨面数据用共享 Bean 造更可控。

### 3.3 亲手改口子（可选）

- 把 `GatewayComponentScan` 的排除正则删掉，重跑 `GatewayIsolationTest`，
  观察 `AdminService` 被装进数据面导致断言失败；再恢复。

## 4. 回归影响

- 数据面不再接受 master key；4.x 用它跑 chat 的测试已改为虚拟 Key。
- 管理查询（Trace/Usage/Audit）不再接受虚拟 Key；按新边界改为 admin 凭证。
- RAG 文档接入不再写 gateway `call_logs`；后续由控制面审计覆盖（REQ-5.2）。

## 5. 验收记录

- 2026-11-16：实现与自检完成，待主人验收。
