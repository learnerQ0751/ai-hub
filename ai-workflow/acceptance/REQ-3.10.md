# REQ-3.10 验收：索引分页加载与限制策略

- 状态：待验收（2026-11-12，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-3.10.md`
- 需求来源：生产化补强第 6 批简报（主人验收反馈）

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] `RagIndexCore.index` 不再调用全量 `chunkManager.listByDocId`，改为游标分页：
      `WHERE doc_id = ? AND chunk_index > ? ORDER BY chunk_index ASC LIMIT ?`
- [x] `RagChunkMapper` 新增 `selectPageByDocId(docId, afterChunkIndex, limit)`；
      保留原 `selectByDocId` 供列表/其他场景使用
- [x] 分页大小可配置（`gateway.rag.index.page-size`），默认与 embedding batch-size 对齐；
      非正数回退 embedding batch-size
- [x] 每页切片按 embedding batch-size 拆分后调用 `embedWithRetry`，成功后 upsert；
      重试语义与批量边界保持不变
- [x] 空文档（0 切片）仍标记 FAILED「文档没有可索引的切片」，与既有行为一致
- [x] 分页索引结果正确：大文档（2000+ chunks 测试构造）全部向量化并置 READY；
      向量数与切片数一致
- [x] 内存行为可断言：每次分页查询条数 ≤ pageSize；分页调用次数 = ceil(chunks / pageSize)
      （最后一页不足 pageSize 时提前结束）
- [x] 失败可重试：单批重试耗尽置 FAILED；reindex 先删旧向量再按当前模型重建并置 READY
- [x] 上传限制策略核对：REQ-3.8 的按类型上限保持有效，本批只在文档中同步说明（无代码重复）
- [x] 非功能：无新增本地业务状态；多实例安全；SQL 使用参数化绑定
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；ArchUnit 依赖方向通过；持久层仍为 MyBatis

**验收命令 / 示例**：

```bash
# 上传包含大量段落的文档，观察索引日志中的 chunks 数并确认 READY
curl -X POST http://localhost:18080/v1/rag/documents \
  -H "Authorization: Bearer <master-key>" \
  -F "file=@large.md" -F "project_id=p-demo"

# 查询文档状态
curl "http://localhost:18080/v1/rag/documents?project_id=p-demo" \
  -H "Authorization: Bearer <master-key>"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.10.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`RagIndexCore`、`RagChunkMapper.xml`

## 3. 实现要点（按需阅读）

### 3.1 三条关键实现要点

1. **游标分页**：`selectPageByDocId` 用 `chunk_index > cursor ORDER BY chunk_index LIMIT n`，
   首页游标 -1；`RagIndexCore` 用本页最后一条的 `chunk_index` 推进，内存只保留一页。
2. **双层批量**：分页大小默认对齐 embedding batch-size；页内仍按 batch-size 拆批调用
   `embedWithRetry`，保持供应商批量上限与重试粒度。
3. **状态与可重入**：`countByDocId` 判空；分页过程中任一批重试耗尽即整篇 FAILED；
   reindex 先删旧向量再重建，向量按 `chunk_id` 幂等覆盖。

### 3.2 面试 1 分钟讲法（骨架）

```text
大文档索引不再一次性 list 出全部切片，而是按 chunk_index 游标分页读，每页默认等于 embedding
批量大小，页内再按供应商上限拆批，向量化后 upsert，游标推进到本页最后一条。这样内存占用是一页
而不是全文；失败重试仍然按批进行，重试耗尽整篇标记 FAILED，可以 reindex 幂等重跑。
```

## 4. 回归影响

- 需重跑的已有验收项：REQ-3.1 文档接入；REQ-3.2 索引/向量；REQ-3.3 检索；
  REQ-3.4 回答；REQ-3.7 embedding；REQ-3.8 上传限制。

## 5. 验收记录

- 验收日期：2026-11-12（自检）
- 结论：实现完成，`mvn -Pquality verify` 全绿（434 测试，0 失败/0 错误/0 跳过；
  Checkstyle/SpotBugs/覆盖率/ArchUnit 通过）。
- 遗留问题：分页只解决索引期全量加载；上传/切分仍为请求内同步，超大文件的分片上传、异步索引与
  对象存储归下一轮架构演进。
