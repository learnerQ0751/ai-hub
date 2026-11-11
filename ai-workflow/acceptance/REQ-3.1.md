# REQ-3.1 验收：文档接入（上传/解析/切分）

- 状态：待验收（2026-11-11，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-3.1.md`
- 需求来源：外部需求卡 `12-项目7-第3期-RAG与记忆服务.md` REQ-3.1

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] `POST /v1/rag/documents`（multipart：`file` + `project_id`）支持 txt / md / pdf；成功返回 201，
      生成文档与切片，切片元数据包含 `doc_id`、`chunk_id`、`project_id`、`source`、`created_at`
- [x] 切片数量与内容抽样正确：段落优先打包；单个超长段落按固定长度 + `overlap` 切分，相邻切片有重叠
- [x] 文件大小上限可通过 `gateway.rag.upload.max-size-bytes` 配置；超限、空文件、不支持的类型返回 400，
      `error.message` 为中文、`type`/`param`/`code` 保持英文
- [x] 解析失败（损坏 PDF / 非 UTF-8 内容 / 空文本）返回 400，并保留原始文件（`rag_documents.raw_content`），
      文档以 `index_status=FAILED` 可查询
- [x] 同一项目重复上传同一文件：默认按 hash 去重，返回已有文档（`duplicated=true`）且不新增切片；
      `gateway.rag.dedup.override=true` 时替换旧文档及其切片（旧切片不可见）
- [x] `GET /v1/rag/documents?project_id=...` 返回该项目的文档列表；不同项目互相隔离
- [x] `DELETE /v1/rag/documents/{docId}?project_id=...` 删除文档并级联删除切片与向量（向量级联随 REQ-3.2 的
      `VectorStore.deleteByDocument` 接入同一删除接口，由 REQ-3.2 测试复核）；删除不存在文档返回 404；跨项目删除返回 404
- [x] RAG 调用登记 `call_logs`：`call_type=RAG`、`target_name=ingest_document/list_documents/delete_document`、`cost=0`，
      且既有 CHAT/MCP/PROMPT/MEMORY 分支回归通过
- [x] 对外错误 `error.message` 为中文，`type` / `param` / `code` / HTTP 状态码保持英文/标准值（STANDARDS §3.6）
- [x] 非功能验收：原始文件落库（非本机目录）；multipart 不缓存请求体；无新增线程池/连接池；多实例无本地业务状态（STANDARDS §2.7）
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；模块依赖方向符合 ArchUnit

**验收命令 / 示例**：

```bash
# 上传（txt / md / pdf 任一）
curl -X POST http://localhost:18080/v1/rag/documents \
  -H "Authorization: Bearer sk-local-master-key" \
  -F "file=@E:/tmp/rag-demo.md" -F "project_id=p-demo"

# 列表
curl -H "Authorization: Bearer sk-local-master-key" \
  "http://localhost:18080/v1/rag/documents?project_id=p-demo"

# 删除
curl -X DELETE -H "Authorization: Bearer sk-local-master-key" \
  "http://localhost:18080/v1/rag/documents/<doc_id>?project_id=p-demo"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.1.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`RagDocumentServiceImpl`、`RagChunker`、`RagDocumentCore`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. 接入链路：`RagDocumentServiceImpl` 校验 project/文件/大小 → `RagDocumentCore` 按 `project_id + SHA-256` 去重 →
   `DocumentTextExtractor` 解析（PDFBox / 严格 UTF-8）→ `RagChunker` 段落贪心打包、超长段落定长 + overlap →
   `rag_documents` + `rag_chunks` 分批落库；原始文件以 BYTEA 落库。
2. 失败与去重：解析失败先持久化 `FAILED` 文档与原始文件，再返回 400；同项目重复 hash 默认返回已有文档（200，
   `duplicated=true`），`gateway.rag.dedup.override=true` 时删除旧文档/切片后重新接入；FAILED 文档重传自动替换重试。
3. 治理与边界：`GatewayGovernanceFilter` 对 `/v1/rag/**` 设置 `callType=RAG`，multipart 不缓存请求体，只做 RPM 预占/结算
   并写 `call_logs`（cost=0）；删除在 Service/Core 事务内先删切片/向量再删文档。

### 3.2 面试 1 分钟讲法（骨架）

```text
文档接入是 RAG 的入口。上传先按项目 + 文件 hash 去重，解析 txt/md/pdf，按段落贪心打包，
超长段落再按固定长度 + overlap 切。原始文件存库，解析失败也能保留并标记 FAILED。
删除时在同一事务里级联切片和向量。治理复用网关统一 Filter，只走 RPM，日志记录 RAG 调用，
成本为 0，不复制限流逻辑。
```

### 3.3 自测题与参考答案

1. 为什么 hash 唯一性按 `project_id + content_hash` 而不是全局？
2. 解析失败为什么还要落一条 FAILED 文档？
3. 段落优先切分与定长切分如何组合？
4. multipart 上传为什么不能走治理 Filter 的请求体缓存？
5. 删除文档时如何保证切片/向量不残留？

### 参考答案

1. 同一份文件在不同项目可能有不同生命周期与权限；按项目隔离去重既避免跨项目误覆盖，也保证项目内幂等。
2. 需求要求保留原始文件；落 FAILED 文档让文件可查询、可删除、可用同文件重新上传自动替换重试，且不产生切片。
3. 先按空行拆段落，短段落贪心打包到 max-chars；单个段落超长时按窗口切分，窗口步长 = max-chars - overlap，
   相邻切片保留上下文重叠。
4. 文件体积大，缓存整段请求体会占内存；RAG 治理只需要操作名做 RPM 与日志，因此直接放行原始请求。
5. 删除在事务内先 `VectorStore.deleteByDocument`（pgvector 与文档同库同事务），再按 doc_id 删切片、删文档；
   内存实现先删向量，DB 失败时重试 reindex 可恢复。

### 3.4 亲手改的口子（可选）

- 任务：把 `gateway.rag.chunk.max-chars` 调小（如 200），重新上传同一文档并开启 `override=true`，观察切片数变化。
- 验证方法：`GET /v1/rag/documents?project_id=...` 的 `chunk_count` 变化；`rag_chunks.content` 抽样。

## 4. 回归影响

- 需重跑的已有验收项：REQ-1.4（RPM 预占/结算）、REQ-1.5（call_logs）、REQ-2.3/2.5/3.5（MCP/PROMPT/MEMORY 分支）。
- 已随 `mvn -Pquality verify` 全量执行（302 个测试，全绿）。

## 5. 验收记录

- 验收日期：2026-11-11
- 结论：实现与 §1 全部对照项自检通过；待主人理解验收（默认通过）
- 验证摘要：新增 33 个测试（切分 4、核心编排 7、文本提取 6、服务 8、Mapper 5、治理 3），
  H2 端到端覆盖上传/去重/解析失败留存/删除/中文错误/RAG 调用日志；PDFBox 真实 PDF 文本提取通过。
- 遗留问题：按 doc_id 下载原始文件的接口未做（原始文件已落库）；向量级联删除由 REQ-3.2 复用同一删除接口。
