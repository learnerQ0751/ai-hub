# REQ-5.2 验收：控制面幂等与拆分配置收口

- 状态：待验收（2026-11-17，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-5.2.md`

## 1. 需求验收标准

> 实现前冻结，唯一勾选点。

- [x] 每个控制面写接口连续调用两次：数据不重复、返回值一致
      （创建类/删除类语义见记录 §2 与 §4.1，均有明确策略）
- [x] 模拟中途失败（第二个部署非法）后重试：别名不落半写，修正后重试成功
- [x] 审计表无重复记录：块/解封、模型创建、部署、授权、MCP 注册、Prompt 创建/发布、
      RAG 接入重复调用均只 1 条
- [x] 可选 `Idempotency-Key`：同键同请求重放首次响应，同键不同请求 409，记录落库
- [x] 两应用共用配置集中在 `application-common.yml`，无同名不同值
- [x] 门禁通过（上游 + 测试模块分批，均 BUILD SUCCESS）

**验收命令**：

```bash
# 同一幂等键重复调用：第二次返回首次结果
IDEM=$(uuidgen)
curl -s -X POST localhost:18081/key/generate -H "Authorization: Bearer $MASTER" \
  -H "Idempotency-Key: $IDEM" -H "Content-Type: application/json" \
  -d '{"models":[],"tenant_id":"t","project_id":"p"}'
curl -s -X POST localhost:18081/key/generate -H "Authorization: Bearer $MASTER" \
  -H "Idempotency-Key: $IDEM" -H "Content-Type: application/json" \
  -d '{"models":[],"tenant_id":"t","project_id":"p"}'   # 同 key，响应带 X-Idempotent-Replay: true

# 不带幂等键：自然键幂等仍生效（例如重复 block）
curl -s -X POST localhost:18081/key/block -H "Authorization: Bearer $MASTER" \
  -H "Content-Type: application/json" -d "{\"key\":\"$PLAIN\"}"   # 两次均 200，审计仅 1 条
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1 + `records/REQ-5.2.md` §2 写路径清单（4 分钟）
2. §3.1 三条实现要点（2 分钟）
3. 核心文件（4 分钟）：`IdempotencyFilter`、`IdempotencyKeyManager`、`V14__idempotency_keys.sql`、
   `AdminServiceImpl`（校验/差异判定）、`ControlPlaneIdempotencyTest`

## 3. 实现要点

### 3.1 三条关键实现要点

1. **状态机条件更新**：Key 封禁/解封只在 `blocked <> 目标值` 时更新，审计跟随受影响行数。
2. **唯一约束兜底**：别名/部署/授权/MCP/Prompt 的冲突异常转成「已存在」语义并读回，
   并发不会 500，重试收敛。
3. **持久化幂等键**：V14 + console 过滤器，按 method+path+body 指纹重放首次响应，
   重启后仍有效；失败释放键允许重试。

## 4. 回归影响

- MCP 同名同配置重复注册由 409 改为 201；集成测试已同步。
- 模型/Prompt 同名同内容重复创建由 400 改为返回既有资源。
- 新增可选请求头，未携带的客户端行为不变。

## 5. 验收记录

- 2026-11-17：实现与自检完成，待主人验收。
