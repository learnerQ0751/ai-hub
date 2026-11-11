# REQ-3.3 验收：检索与重排

- 状态：待验收（2026-11-11，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-3.3.md`
- 需求来源：外部需求卡 `12-项目7-第3期-RAG与记忆服务.md` REQ-3.3

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] `POST /v1/rag/search` 接收 `query`、`top_k`、`project_id`、元数据过滤 `filters`（`doc_ids` / `source`）、
      相似度阈值 `min_score` 与重排开关 `rerank`；返回切片 `content`、`source`、`score`、`doc_id`、`chunk_id`
- [x] 已知文档提问命中正确切片：以已知语料上传后检索，结果首条切片包含问题对应的关键内容（集成测试 + 真实链路人工核对）
- [x] `project_id` 隔离：A 项目上传的文档不会出现在 B 项目的检索结果中
- [x] `top_k` 生效：返回条数不超过请求值（且不超过配置上限）；超上限收敛、非法值（<=0）返回 400
- [x] `min_score` 生效：低于阈值的切片不返回；阈值越界（不在 [-1,1]）返回 400
- [x] 元数据过滤生效：`filters.doc_ids` 与 `filters.source` 只能缩小结果集，不会引入其他项目/文档的切片
- [x] 无命中：返回 HTTP 200、`results=[]`、`count=0`、`no_hit=true`，不抛错
- [x] 重排：服务端 `gateway.rag.search.rerank-enabled` 可开关；开启时按规则重排（向量分 + query 词项覆盖率），
      请求 `rerank=false` 可关闭；重排结果（chunk_id + 分数）写日志；无 query 词项时保持原序
- [x] 检索日志：记录 `query`、召回 `chunk_id + score`、候选数、耗时与 `no_hit`（同一 `request_id`），
      不记录切片正文；日志可用于 Badcase 归因
- [x] RAG 调用登记 `call_logs`：`call_type=RAG`、`target_name=search_knowledge`、`cost=0`；既有
      CHAT/MCP/PROMPT/MEMORY/RAG 分支回归通过
- [x] 对外错误 `error.message` 为中文，`type` / `param` / `code` / HTTP 状态码保持英文/标准值（STANDARDS §3.6）
- [x] 非功能验收：无新增线程池/连接池；切片批量补齐每批 ≤100 条、候选上限 200；多实例无本地业务状态（STANDARDS §2.7）
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；模块依赖方向符合 ArchUnit

**验收命令 / 示例**：

```bash
# 上传已知语料（见 REQ-3.1），等待 index_status=READY
curl -X POST http://localhost:18080/v1/rag/documents \
  -H "Authorization: Bearer sk-local-master-key" \
  -F "file=@E:/tmp/rag-demo.md" -F "project_id=p-demo"

# 检索
curl -X POST http://localhost:18080/v1/rag/search \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"query":"年假有多少天","project_id":"p-demo","top_k":3,"min_score":0.0,"rerank":true}'

# 带元数据过滤
curl -X POST http://localhost:18080/v1/rag/search \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"query":"报销流程","project_id":"p-demo","filters":{"source":"员工手册.md"}}'

# 无命中（不存在的项目/主题）
curl -X POST http://localhost:18080/v1/rag/search \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"query":"火星基地建设","project_id":"p-demo","top_k":5}'
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.3.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`RagRetrievalCore`、`RagLexicalReranker`、`RagSearchServiceImpl`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. 检索编排：`RagRetrievalCore` 先 `embed(query)`，再以 `project_id + 当前模型` 调 `VectorStore.search`；
   召回条数按 `top_k × candidate-multiplier` 放大（上限 200），解决“过滤/重排后候选不足”的问题。
2. 过滤与重排：向量结果只有 id/score，按 chunk_id 分批（≤100）补 `content/source/chunk_index` 后执行
   `doc_ids/source` 过滤；重排开启时按 `vectorScore×(1-w) + 词项覆盖率×w` 重算分数，服务端总开关
   `gateway.rag.search.rerank-enabled` 可关闭，请求只能关闭不能越权打开。
3. 语义与可观测：无命中统一返回 200 + `no_hit=true`，让调用方区分“知识库没有”与“服务异常”；检索日志只记
   query、chunk_id/score、候选数与耗时，不落正文，便于 Badcase 归因且不泄露文档内容。

### 3.2 面试 1 分钟讲法（骨架）

```text
RAG 检索入口先做参数校验和收敛：top_k 有默认值和上限，min_score 限定在 [-1,1]。
检索编排在 core：query 向量化后按项目 + 模型版本查 pgvector，召回条数按倍数放大，
再按 chunk_id 批量补齐正文与来源，执行 doc_ids/source 元数据过滤。重排是可选规则重排，
用向量分和查询词项覆盖率的加权和重算排序，服务端开关控制，避免成本失控。
无命中统一返回空列表加 no_hit，不抛错。检索日志记录 query、命中 id 和分数，
不记录正文，既能做 badcase 归因又不泄露文档内容。
```

### 3.3 自测题与参考答案

1. 为什么召回条数要按 `top_k` 放大，而不是直接取 `top_k`？
2. 元数据过滤为什么不直接改 `VectorSearchQuery` 下推 SQL？
3. 规则重排如何计算分数？为什么不做 LLM 重排？
4. 无命中为什么返回 200 + `no_hit` 而不是 404？
5. 检索日志为什么不能记录切片正文？如何既记录又便于归因？

### 参考答案

1. 向量检索先取 top_k 后，元数据过滤和重排可能淘汰部分候选；放大召回（有上限）保证过滤后仍有足够结果，
   否则会人为造成“召回不足”。
2. 本轮保持 REQ-3.2 的端口契约不变，过滤在检索编排层完成；代价是候选放大，用上限 200 与分批补齐控制资源；
   后续如需更强性能，可扩展端口把 doc_id 过滤下推 SQL。
3. 把 query 按英文单词/数字与中文 bigram 切词，计算切片命中词项比例；最终分 = 向量分 × (1-w) + 覆盖率 × w。
   LLM 重排会增加一次模型调用成本与延迟，需求允许二选一，规则重排可解释、可离线测试，故本期选规则重排。
4. 无命中是正常业务结果而非错误；200 + `no_hit` 让调用方可分支处理（如走兜底话术），与 500 的服务异常区分开。
5. 切片正文属于业务文档内容，日志落地后难以回收；只记录 query、chunk_id/score 与耗时，配合 request_id
   与检索快照可还原归因链路，同时满足隐私约束。

### 3.4 亲手改的口子（可选）

- 任务：把 `gateway.rag.search.candidate-multiplier` 从 4 改为 1，观察带元数据过滤时的返回条数变化。
- 验证方法：同一 query + `filters.source` 检索，对比修改前后 `count` 与日志中的候选数。

## 4. 回归影响

- 需重跑的已有验收项：REQ-3.1（上传/切分/列表/删除）、REQ-3.2（reindex/向量存储）、REQ-1.4（RPM）、
  REQ-1.5（call_logs）、REQ-2.3/2.5/3.5（call_type 分支）。

## 5. 验收记录

- 验收日期：2026-11-11（自检）
- 结论：实现完成，`mvn -Pquality verify` 全绿（347 个测试、0 失败）；新增 13 个 core 测试、
  10 个 service 测试与 8 个 H2 端到端测试，覆盖命中、隔离、阈值/条数、元数据过滤、无命中、
  重排开关、中文错误与 `search_knowledge` 调用日志；pgvector 真库回归通过。
- 遗留问题：离线确定性 embedding 无语义能力，真实语义命中演示随 REQ-3.4 的真实模型链路完成；
  检索日志采用应用日志（不建表），按 `request_id` 检索；master 理解验收待确认。
