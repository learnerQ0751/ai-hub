# REQ-1.3 验收：虚拟 Key

- 状态：已验收（2026-10-30，理解默认通过）
- 验收人：
- 需求记录：`../records/REQ-1.3.md`
- 需求来源：Vault `05-项目7-第1期-LLM网关核心.md` §REQ-1.3

## 1. 需求验收标准

- [x] `POST /key/generate` → 返回 `sk-` 明文；DB 查询仅存 hash（明文不可见）
- [x] 用虚拟 key 调用授权模型 → 200，响应正常
- [x] 调用未授权模型 → 403，`error.type=key_model_access_denied`、`param=model`、`code=403`
- [x] `/key/block` 后调用 → 401 `auth_error`；`/key/unblock` 恢复
- [x] 过期 key → 401；`/key/info` 字段与基线一致（key/info/spend/models/expires）
- [x] `mvn -Pquality verify` 通过

**验收命令 / 示例**：

```bash
curl -X POST http://localhost:18080/key/generate \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"models":["chat"],"duration":"30d"}'
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-1.3.md` §1-3 人向说明（3 分钟）
3. 本文 §3 设计图（本次增量）+ 核心文件 `AuthFilter` / `KeyServiceImpl` / `ChatCompletionCore`（5 分钟）

## 3. 设计图（drawio 导出 SVG）

![ai-hub 架构与运行链路](assets/design.svg)

> 本次增量：Key 管理链路（KeyController → KeyService → ApiKeyManager → PostgreSQL）+ AuthFilter 的虚拟 Key 鉴权分支：哈希校验与白名单。 全局设计说明见 `../DESIGN.md`。

## 4. 实现要点与参考答案（按需阅读）

### 4.1 三条关键实现要点

1. **哈希存储 + 一次性明文**：生成时返回 `sk-`，库中只有 SHA-256；`/key/info` 只回显掩码。
2. **授权顺序**：先白名单（403 `key_model_access_denied`）后未知模型（400 `invalid_model`），避免状态码差异泄露模型目录。
3. **管理面 / 数据面隔离**：`/key/*` 仅 master；虚拟 Key 只能走 chat 数据面。

### 4.2 面试 1 分钟讲法（骨架）

```text
我实现了网关的虚拟 Key 体系：生成时返回一次明文，库中只存 SHA-256；请求进来先哈希查库，
校验封禁与过期；再按 Key 的模型白名单授权，越权统一 403；管理接口只允许 master；
Key 状态全部外置数据库，多实例一致。
```

### 4.3 自测题与参考答案

1. 一个虚拟 Key 请求 `chat` 时，从 `Authorization` 到最终选部署，中间经过哪些校验？每类失败的 HTTP 状态码和 `error.type` 分别是什么？
2. 为什么数据库只存 SHA-256、不存明文？`/key/info` 又如何返回“key”字段？如果 Key 泄露，止损路径是什么？
3. 白名单校验为什么放在 core 而不是 Controller？如果放在 Controller 会带来什么分层问题？
4. `models` 为空、`models=["chat"]`、master key 三种情况，对 `reasoner` 的访问结果分别是什么？为什么？
5. `/key/block` 之后，已经发出去的请求会怎样？多实例部署时，封禁状态为什么必须放数据库/Redis 而不是本机内存？

### 参考答案

1. AuthFilter 解析 Bearer：等于 master 直接通过；否则 SHA-256 查 `api_keys`，不存在/封禁/过期 → 401 `auth_error`；`/key/*` 非 master → 401。Core 白名单不含 alias → 403 `key_model_access_denied`（`param=model`）；部署为空 → 400 `invalid_model`。
2. 明文只在生成时返回一次，库中哈希降低泄露风险；`/key/info` 只回显后 4 位掩码。泄露后的止损：立即 `/key/block` 封禁并排查调用日志，必要时轮换 master key。
3. 授权是业务规则，属于 core 编排；放 Controller 会破坏"先 403（越权）后 400（未知模型）"的统一顺序，且无法被其他入口复用与测试。
4. `models` 为空 = 不限制 → 允许访问 reasoner；`models=["chat"]` → reasoner 403；master key → 允许。因为 core 只在"非 master 且白名单非空且不含 alias"时返回 403。
5. 已进入处理中的请求不受影响（鉴权在入口一次性完成）；后续请求 401。多实例内存不共享，状态必须放 DB/Redis 才能让所有实例立即一致。

### 4.4 亲手改的口子（可选）

- 任务：把 `KeyManagementIntegrationTest` 中生成 Key 的 `models` 从 `["chat"]` 改为 `["reasoner"]`，重跑测试，观察哪个用例的 200/403 预期发生变化并解释原因。
- 验证方法：`mvn -pl ai-hub-tests -am test -Dtest=KeyManagementIntegrationTest`。

## 5. 回归影响

- REQ-1.1：聊天链路（鉴权扩展后仍支持 master key 调用）
- REQ-1.2：`/models` 与多部署路由不受影响

## 6. 验收记录

- 验收日期：2026-10-30
- 结论：工程验收通过；理解验收默认通过（如需深挖看 §4）
- 遗留问题：无
