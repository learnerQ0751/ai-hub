# REQ-3.2 验收：Embedding 抽象 + 向量存储

- 状态：待验收（2026-11-11，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-3.2.md`
- 需求来源：外部需求卡 `12-项目7-第3期-RAG与记忆服务.md` REQ-3.2

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] 存在 `EmbeddingModel` 抽象（文本 → 向量、模型标识、维度）；默认实现为 LangChain4j 本地
      AllMiniLmL6V2（384 维），`gateway.rag.embedding.provider` 可切换；测试/离线可用确定性实现
- [x] 存在 `VectorStore` 抽象；默认 PostgreSQL pgvector 实现（`rag_vectors`，`vector(384)`，HNSW cosine 索引）；
      提供内存实现用于测试；存储条目携带 `chunk_id`/`doc_id`/`project_id`/`embedding_model`/`embedding_dimension`
- [x] 上传文档后切片批量向量化，状态 PENDING → PROCESSING → READY；向量可检索，topK 返回正确切片；
      不同 project 的向量互相隔离；不同 embedding 模型版本互相隔离
- [x] 批量向量化按 `batch-size` 分批，失败按 `max-retries` 重试；向量/模型/维度元数据正确记录在文档与切片
- [x] 向量化或向量存储异常时文档/切片状态为 FAILED 且记录 `error_message`；`POST /v1/rag/documents/{docId}/reindex`
      可重试并恢复 READY
- [x] 切换 embedding 模型后，已有文档列表返回 `reindex_required=true`（有提示）；reindex 后向量按新模型版本重建、
      `reindex_required=false`
- [x] 成本/耗时记录：`embedding_cost`（本地模型为 0）与 `embedding_duration_ms` 落文档元数据；列表可见
- [x] 删除文档级联删除 `rag_vectors`（pgvector 与内存实现都验证）
- [x] RAG 调用登记 `call_logs`：`call_type=RAG`、`target_name=reindex_document`、`cost=0`
- [x] 对外错误 `error.message` 为中文，`type` / `param` / `code` / HTTP 状态码保持英文/标准值（STANDARDS §3.6）
- [x] 非功能验收：本地 embedding 专用线程池显式参数、Bean 销毁关闭；无 commonPool；批量有界；多实例无本地业务状态（STANDARDS §2.7）
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；pgvector 实现有 Testcontainers 真库验证（无 Docker 时跳过）
- [x] 模块依赖方向符合 ArchUnit；core 不依赖治理组件

**验收命令 / 示例**：

```bash
# 上传（见 REQ-3.1），查看 index_status / embedding_model / chunk_count
curl -H "Authorization: Bearer sk-local-master-key" \
  "http://localhost:18080/v1/rag/documents?project_id=p-demo"

# 重试索引
curl -X POST -H "Authorization: Bearer sk-local-master-key" \
  "http://localhost:18080/v1/rag/documents/<doc_id>/reindex?project_id=p-demo"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.2.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`RagIndexCore`、`PgVectorStore`、`LangChain4jEmbeddingModel`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. 双端口抽象、配置装配：`EmbeddingModel`（LangChain4j 本地 all-minilm-l6-v2 为默认，确定性实现供测试/离线）与 `VectorStore`（pgvector 为生产默认，内存实现供测试）按 `gateway.rag.*` 条件装配，业务层只依赖接口。
2. 索引编排在 core、状态机可恢复：`RagIndexCore` 按 `batch-size` 分批向量化，失败按 `max-retries` 重试（带 backoff），状态 PENDING → PROCESSING → READY/FAILED 落库；`reindex` 先删旧向量再重建，模型切换/维度隔离都走这条路。
3. 存储与检索下推：`rag_vectors` 使用 `vector(384)` + HNSW cosine 索引，`project_id`、`embedding_model` 写入并下推过滤，保证租户与模型版本隔离；删除文档级联删除向量。

### 3.2 面试 1 分钟讲法（骨架）

```text
RAG 的向量层做了两层抽象：EmbeddingModel 和 VectorStore，默认实现是本地 ONNX 模型（384 维）
和 pgvector（HNSW cosine 索引）。索引编排在 core：文档切片分批向量化，失败按配置重试，
状态机 PENDING → PROCESSING → READY/FAILED 全部落库。pgvector 表按 project_id 与模型版本
隔离，换模型时走 reindex：先删旧向量再按新模型重建，并给调用方 reindex_required 提示。
线程池用显式参数的 ThreadPoolExecutor、Bean 销毁时关闭；索引是请求内同步完成，多实例无本地状态。
```

### 3.3 自测题与参考答案

1. 为什么要把 EmbeddingModel 和 VectorStore 抽象出来？
2. 索引状态机怎么设计？失败后如何恢复？
3. 切换 embedding 模型为什么必须重建索引？如何提示调用方？
4. pgvector 索引为什么选 HNSW + cosine？
5. 多实例部署下索引流程有什么风险？当前实现如何规避？

### 参考答案

1. 避免绑定单一供应商/存储：本地模型可换云端 API，pgvector 可换其他向量库；测试可用确定性实现与内存存储，不依赖外部服务。
2. PENDING → PROCESSING → READY/FAILED，状态与错误落库；异常时 markFailed 记录错误摘要；`reindex` 先删旧向量再重建，恢复 READY。
3. 不同模型的向量空间与维度不同，混用会导致检索失真；切换后文档列表返回 `reindex_required=true`，重建后置 false，向量按 `embedding_model` 标识隔离。
4. HNSW 查询快、适合本项目规模；cosine 适合归一化文本嵌入；project_id/模型过滤条件下推 SQL，保证租户隔离。
5. 当前索引是请求内同步完成，无后台线程/调度，天然多实例安全；若未来异步化，需要 DB 状态 + 乐观锁/选举避免重复执行。

### 3.4 亲手改的口子（可选）

- 任务：把 `gateway.rag.embedding.batch-size` 从默认值改为 8，观察分批调用变化。
- 验证方法：重启后上传较长文档，看日志中 `embedAll` 调用次数与每批条数是否符合预期。

## 4. 回归影响

- 需重跑的已有验收项：REQ-3.1（文档接入：索引状态/元数据字段）、REQ-1.5（call_logs 新增 RAG 类型）、应用启动配置（`gateway.rag.*`）。

## 5. 验收记录

- 验收日期：2026-11-11（自检）
- 结论：实现完成，`mvn -Pquality verify` 全绿（375 测试、0 失败）；pgvector 真库 Testcontainers 验证通过（`RagVectorStoreIntegrationTest`，真实容器 17.3s：Flyway V1–V8 迁移 + upsert 幂等 + project/模型隔离检索 + 级联删除）。
- 遗留问题：无 Docker 的环境中 `RagVectorStoreIntegrationTest` 按 `disabledWithoutDocker` 自动跳过；master 理解验收待确认。
