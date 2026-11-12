# REQ-3.8 验收：计费与边界补强

- 状态：待验收（2026-11-12，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-3.8.md`
- 需求来源：生产化补强第 5 批简报（主人验收反馈）

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] RAG answer 内部 chat 的 usage 与 deploymentModel 回填到治理上下文；
      `call_logs` 记录实际 usage/cost（不再固定 0），`api_keys.spend` 按实际成本累计
- [x] RAG answer 采用「入口 RPM + 事后实际 usage 计价并做预算校验」：响应成功后检查预算上限并
      原子累计成本；超出预算时记录可观测错误但不改变已成功响应；设计决策写入 REQ §5
- [x] RAG answer 成本包含内部 chat 成本与查询 embedding 成本；无召回兜底（不调模型）也计入查询
      embedding 成本
- [x] RAG 检索/文档接口保持 RPM-only（不做 TPM/预算预占与拒绝）；检索的查询 embedding 成本
      记录到 `call_logs.cost`，文档索引成本记录到 `rag_documents.embedding_cost`
- [x] chunk 默认大小按 embedding 模型 `max-input-tokens × 0.8` 推导（中文按 1 字≈1 token，
      上限 800 字）；显式配置优先
- [x] 启动/首次索引时校验 chunk 配置与模型窗口：超出安全上限按配置 WARNING 或启动失败
      （`gateway.rag.chunk.overflow-policy=warn|fail`）
- [x] 上传限制按文件类型配置（pdf / txt / md / docx 等），未命中类型回退默认 10MB；
      超限返回中文 400 错误且 `param=file`
- [x] Spring multipart 硬上限与最大类型限制匹配并在配置注释中说明；Service 按类型精确校验
- [x] 对外错误 `error.message` 为中文，`type/param/code`/HTTP 状态码保持英文/标准值
- [x] 非功能：Redis 预算原子操作、故障 fail-open；无新增本地业务状态（STANDARDS §2.7）
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；ArchUnit 依赖方向通过；持久层仍为 MyBatis

**验收命令 / 示例**：

```bash
# RAG answer 后查看调用日志成本（需先配置 chat 价格）
curl -X POST http://localhost:18080/v1/rag/answer \
  -H "Authorization: Bearer <virtual-key>" -H "Content-Type: application/json" \
  -d '{"model":"chat-default","project_id":"p-demo","query":"年假几天"}'

# 上传超限示例：pdf 超过配置上限返回 400 中文错误
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.8.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`GatewayGovernanceService`、`RagChunkSettings`、`RagDocumentServiceImpl`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **RAG answer 事后结算**：`RagAnswerResDTO` 内部携带 deploymentModel/embedding 用量（`@JsonIgnore`），
   Controller 回填 `GatewayContext`；治理层 `completeRagCall` 计算 chat + embedding 总成本，
   用 `chargeAfterwards` 检查预算并原子累计，成功响应后写 `call_logs` 与 `api_keys.spend`。
2. **chunk 推导与校验**：`RagChunkSettings` 按 `max-input-tokens × 0.8` 推导（中文 1 字≈1 token，
   上限 800），显式 `max-chars` 优先；超出安全窗口按 `overflow-policy=warn|fail` 告警或启动失败。
3. **上传限制差异化**：`RagProperties.Upload.typeLimits` 按扩展名配置上限，Service 在 multipart
   解析后精确校验；未命中类型回退默认 10MB，multipart 只做 50MB 硬上限。

### 3.2 面试 1 分钟讲法（骨架）

```text
RAG answer 的 prompt 由检索结果动态拼装，入口无法按 messages 预估 token，所以入口只做 RPM，
响应后把内部 chat 的 usage 和 deploymentModel 回填治理上下文，按实际成本计价、做预算校验并
累计 spend。检索/文档仍保持 RPM-only，但 embedding 成本会记录。chunk 大小按 embedding 模型窗口
乘 0.8 推导并校验，上传限制按文件类型配置，multipart 只是硬边界。
```

### 3.3 自测题与参考答案

1. 为什么 RAG answer 不能像 chat 一样在入口预占 TPM/预算？
2. 事后预算校验如何处理“响应已经成功”的事实？
3. chunk 800 字在 all-minilm（256 token）下有什么问题？如何修复？
4. 上传类型限制与 multipart 上限是什么关系？
5. 检索接口保持 RPM-only，embedding 成本还能看到吗？

### 参考答案

1. prompt 由检索命中动态拼装，入口拿不到最终 messages；只能先做 RPM 防滥用，事后按真实 usage 结算。
2. 不能回滚响应；`chargeAfterwards` 先检查上限再无条件累计实际成本，保证后续请求看到真实 spend，
   超限时在 `call_logs.error_type` 记录 `budget_exceeded` 供观测。
3. 800 字中文≈800 token，超过 256 token 窗口会被供应商截断，召回语义丢失；按窗口×0.8 推导得到
   204 字，或切换到 8192 token 的 v3。
4. multipart 是框架硬上限（防打爆），类型限制是业务上限（按扩展名精确拒绝），前者必须 ≥ 最大类型值。
5. 可以；Controller 回填查询 embedding 用量，治理层把成本写入 `call_logs.cost`，但不做预算扣减。

### 3.4 亲手改的口子（可选）

- 任务：把 `gateway.rag.chunk.overflow-policy` 改为 `fail`，并把 `max-chars` 设为 5000，
  重启观察启动失败；再改回 `warn` 观察 WARNING。
- 验证方法：查看启动日志中的 `chunk maxChars=... exceeds ...` 信息。

## 4. 回归影响

- 需重跑的已有验收项：REQ-1.4 预算/限流；REQ-1.5 call_logs；REQ-3.1 上传；REQ-3.3 检索；
  REQ-3.4 回答；REQ-3.7 embedding 治理。

## 5. 验收记录

- 验收日期：2026-11-12（自检）
- 结论：实现完成，`mvn -Pquality verify` 全绿（453 测试，0 失败/0 错误/0 跳过）。
- 遗留问题：RAG answer 事后预算校验无法阻止本次已成功响应；超出预算仅在日志留痕，后续请求
  会被 Redis 已累计的 spend 拦截。主人验收时如需更严格，可改为按检索命中后的估算预占。
