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

## 3. 实现要点（按需阅读）

### 3.1 三条关键实现要点

1. **扫描范围显式化**：两个启动类不扫根包；gateway 正则排除 `service.admin`，
   console 只扫 `service.shared` + `service.admin` + 管理所需 integration/core.rag。
2. **鉴权不共用**：gateway 的 `AuthFilter` 遇 master key 直接 403；
   console 的 `AdminAuthFilter` 遇虚拟 Key 直接 403；两套 Filter 互不加载。
3. **Flyway 单归属**：console 开、gateway 关；部署顺序 console healthy → gateway。

## 4. 回归影响

- 数据面不再接受 master key；4.x 用它跑 chat 的测试已改为虚拟 Key。
- 管理查询（Trace/Usage/Audit）不再接受虚拟 Key；按新边界改为 admin 凭证。
- RAG 文档接入不再写 gateway `call_logs`；后续由控制面审计覆盖（REQ-5.2）。

## 5. 验收记录

- 2026-11-16：实现与自检完成，待主人验收。
