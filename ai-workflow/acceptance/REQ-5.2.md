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

## 3. 实现要点与参考答案

### 3.1 三条关键实现要点

1. **状态机条件更新**：Key 封禁/解封只在 `blocked <> 目标值` 时更新，审计跟随受影响行数。
2. **唯一约束兜底**：别名/部署/授权/MCP/Prompt 的冲突异常转成「已存在」语义并读回，
   并发不会 500，重试收敛。
3. **持久化幂等键**：V14 + console 过滤器，按 method+path+body 指纹重放首次响应，
   重启后仍有效；失败释放键允许重试。

### 3.2 自测题与参考答案

1. 为什么 Key 封禁不需要幂等键？
2. 为什么创建模型要先校验全部部署再写别名？
3. 为什么幂等键要落库而不是放内存？
4. 为什么同一个幂等键配不同请求要返回 409？
5. 为什么 RAG 文档接入不需要幂等键？

### 参考答案

1. 它是对已有资源的条件状态更新，重复执行自然收敛；审计按变化去重。
2. 别名与部署是两张表，先写别名再校验会在失败时留下半写；先校验可保证失败无副作用。
3. 重启后内存丢失会让重放变成二次执行，重复创建 Key/版本。
4. 同键代表「同一个操作」；请求不同说明客户端复用键，必须拒绝而不是猜测意图。
5. 接入按内容哈希去重，重复上传返回既有文档，本身已幂等。

### 3.3 亲手改口子（可选）

- 去掉 `ApiKeyMapper.updateBlocked` 的 `AND blocked <> ?`，重跑
  `ControlPlaneIdempotencyTest#key_block_and_unblock_are_idempotent_and_audited_once`，
  观察审计变为 2 条；恢复。

## 4. 回归影响

- MCP 同名同配置重复注册由 409 改为 201；集成测试已同步。
- 模型/Prompt 同名同内容重复创建由 400 改为返回既有资源。
- 新增可选请求头，未携带的客户端行为不变。

## 5. 验收记录

- 2026-11-17：实现与自检完成，待主人验收。
