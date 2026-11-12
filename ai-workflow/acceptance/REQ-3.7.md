# REQ-3.7 验收：Embedding 一等能力（阿里 text-embedding-v3）

- 状态：待验收（2026-11-12，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-3.7.md`
- 需求来源：生产化补强第 5 批简报（主人验收反馈）

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] embedding 模型目录可配置多个条目：别名、provider、真实模型、维度、最大输入 token、
      每 1M tokens 输入单价、base-url、API Key 环境变量；支持配置切换；默认条目为
      阿里 DashScope `text-embedding-v3`（OpenAI 兼容模式）
- [x] 旧单模型配置（`gateway.rag.embedding.provider/model-id/dimension/...`）仍可工作，
      测试环境继续使用确定性实现，不依赖真实 API Key
- [x] 存在 OpenAI 兼容远端实现 `RemoteEmbeddingModel`：`POST {base-url}/embeddings`；
      按供应商批量上限拆分（阿里 v3 = 10 条，官方文档确认）；可配置重试次数 + 退避、
      连接/读超时；逐向量校验维度与配置一致
- [x] 远端错误映射为中文 `error.message`，`type/param/code/HTTP 状态码` 保持英文/标准值；
      典型：鉴权失败、限流、5xx、超时、响应非法、未配置 API Key、单条文本超窗口
- [x] 新增 `POST /v1/embeddings`：OpenAI 兼容 `input: string | string[]`、`model` 别名、
      可选 `dimensions`；返回 `object=list`、`data[].embedding/index`、`usage.prompt_tokens/total_tokens`
- [x] `/v1/embeddings` 走统一治理：RPM、TPM（输入 token）、预算预占/结算、实际成本累计 spend、
      `call_logs.call_type=EMBEDDING`；未知模型/白名单外模型返回既有中文错误
- [x] embedding 计量优先使用供应商返回 `usage.prompt_tokens`；缺失时按确定性估算
      （CJK 1 字≈1 token，其他字符 4 字符≈1 token）；估算规则写入 REQ §5
- [x] embedding 计价按模型条目「每 1M tokens 输入单价」计算，金额 BigDecimal 8 位小数；
      本地/确定性模型单价为 0
- [x] RAG 索引 embedding 成本写入 `rag_documents.embedding_cost`（不再固定 0）；
      本地/确定性实现仍为 0；成本与 token 用量可观测
- [x] RAG 检索/回答的查询 embedding 成本记录到对应 `call_logs.cost`；
      回答调用成本 = 内部 chat 成本 + 查询 embedding 成本
- [x] 配置切换模型后，已有文档列表按模型/维度返回 `reindex_required` 提示；
      pgvector 单列兼容 384/512/768/1024 维（零填充），cosine 语义不变
- [x] 资源生命周期：远端 HTTP 有连接/读取超时；本地模型专用线程池显式参数并在销毁时关闭；
      批次与输入条数有界；多实例无本地业务状态（STANDARDS §2.7）
- [x] 无 API Key 时应用可正常启动；单元/集成测试使用 MockWebServer 或确定性实现；
      阿里真实联调作为待确认项记录，不阻塞其余交付
- [x] 仓库不包含 API Key/密钥/本地配置；`git status` 干净可提交
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；ArchUnit 依赖方向通过；持久层仍为 MyBatis

**验收命令 / 示例**：

```bash
# 单条
curl -X POST http://localhost:18080/v1/embeddings \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"model":"text-embedding-v3","input":"企业知识库检索"}'

# 批量 + 指定维度
curl -X POST http://localhost:18080/v1/embeddings \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"model":"text-embedding-v3","input":["第一段","第二段"],"dimensions":768}'
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.7.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`RemoteEmbeddingModel`、`ConfigurableEmbeddingModelProvider`、
   `GatewayGovernanceService`（EMBEDDING 分支）

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **模型目录与装配**：`RagProperties.Embedding.models` 支持多别名条目（provider/model/维度/
   窗口/单价/base-url/Key 环境变量）；`ConfigurableEmbeddingModelProvider` 构建只读目录，
   旧单模型字段仍可用；RAG 通过 `@Primary EmbeddingModel` 使用 `gateway.rag.embedding.model`
   指定的主模型。
2. **远端实现**：`RemoteEmbeddingModel` 用 JDK HttpClient（连接/读超时）调用 OpenAI 兼容
   `/embeddings`；按 `max-batch-size`（v3=10）拆批，可配置重试 + 退避；优先使用供应商
   `usage.prompt_tokens`，缺失时按 CJK 1 字≈1 token / 其他 4 字符≈1 token 估算；响应按 index
   排序并校验维度；鉴权/限流/5xx/超时中文映射，响应级错误不重试。
3. **统一治理与成本归集**：`/v1/embeddings` 由 `GatewayGovernanceFilter` 识别为
   `CallTypes.EMBEDDING`，`GatewayGovernanceService` 复用 RPM/TPM/预算预占与结算，按模型单价
   计价并写 `call_logs`；RAG 索引成本写 `rag_documents.embedding_cost`，检索/回答查询 embedding
   成本写 `call_logs.cost`；向量列 `vector(1024)` 对低维模型零填充，cosine 语义不变。

### 3.2 面试 1 分钟讲法（骨架）

```text
embedding 不再只是 RAG 的内部细节：模型目录支持多个别名，每条带维度、窗口、单价和
OpenAI 兼容地址；默认阿里 text-embedding-v3（8192 token，批量 10）。远端实现负责拆批、
重试、超时、维度校验和 usage 解析，缺失 usage 时用确定性估算。新入口 /v1/embeddings
直接复用网关的限流/预算/计价/日志治理，call_type=EMBEDDING。RAG 索引把成本写文档，
检索/回答把查询 embedding 成本写 call_logs；pgvector 列固定 1024 维，低维模型零填充兼容。
```

### 3.3 自测题与参考答案

1. 为什么 embedding 模型目录要保留旧单模型字段？
2. 阿里 v3 的批量与窗口限制是什么？代码如何落实？
3. 供应商没有返回 usage 时如何计量？为什么这样估算？
4. 为什么向量列固定 1024 维还能支持 384 维模型？
5. `/v1/embeddings` 如何复用治理而不复制限流/预算逻辑？

### 参考答案

1. 测试/离线环境仍可使用确定性模型，历史配置无需迁移；新目录为空时回退旧字段，保证加法演进。
2. 官方限制：单次最多 10 条、单条 8192 token；`max-batch-size=10` 拆批，`max-input-tokens`
   在调用前校验，chunk 参数按其 0.8 推导。
3. 用 `EmbeddingTokenEstimator`：CJK 1 字≈1 token，其他 4 字符≈1 token 向上取整；对中文保守，
   不会低估成本与窗口占用。
4. cosine 距离对零填充不变；`PgVectorStore` 写入与查询都把向量补齐到 1024，实际维度仍记录在
   `embedding_dimension`，因此 384/512/768/1024 可共存。
5. Filter 统一识别路径与 callType，Service 只解析别名与估算输入 token，治理服务复用同一套
   Guard/计价/日志；新增接口只需在 `CallTypes` 与 Filter 提取处扩展。

### 3.4 亲手改的口子（可选）

- 任务：把 `gateway.rag.embedding.models.text-embedding-v3.input-per-million` 从 `0.5`
  改为 `0.6`，重启后调用 `/v1/embeddings` 观察 `call_logs.cost` 变化。
- 验证方法：用虚拟 Key 发起请求，查询 `call_logs` 的 `usage` 与 `cost`，确认按新单价计算。

## 4. 回归影响

- 需重跑的已有验收项：REQ-1.1~1.8 聊天/限流/预算/日志；REQ-2.x MCP/Prompt；
  REQ-3.1~3.6 RAG/记忆/MCP；`RagVectorStoreIntegrationTest`（V9 维度迁移与零填充）。

## 5. 验收记录

- 验收日期：2026-11-12（自检）
- 结论：实现完成，`mvn -Pquality verify` 全绿（453 测试，0 失败/0 错误/0 跳过；Checkstyle 0、
  SpotBugs 0、覆盖率达标、ArchUnit 通过、pgvector Testcontainers 回归通过）。
- 遗留问题：阿里 DashScope 真实调用直连返回 401（环境 Key 存在但被拒），需主人确认有效
  Key/Workspace base-url；应用内远端链路由 MockWebServer 覆盖，不阻塞其余交付。
