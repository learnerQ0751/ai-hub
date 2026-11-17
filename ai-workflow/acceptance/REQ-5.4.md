# REQ-5.4 验收：切片顺序还原与上下文扩展

- 状态：待验收（2026-11-17，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-5.4.md`

## 1. 需求验收标准

> 实现前冻结，唯一勾选点。

- [x] 同一文档多片段命中时，上下文按 `chunk_index` 原文顺序且按文档分组
- [x] 邻居扩展前后各 n 条（默认 1），去重且不超字符预算
- [x] 引用只来自原命中 `chunk_id`；扩展邻居标记为上下文、不进入 citations
- [x] 3.10 游标分页用例不回退
- [x] 同一输入多次拼装结果一致（稳定排序）
- [x] 门禁通过

**验收命令**：

```bash
# 上传一篇多段落文档后提问（虚拟 Key 绑定租户+项目）
curl -s -X POST localhost:18080/v1/rag/answer \
  -H "Authorization: Bearer $VIRTUAL" -H "Content-Type: application/json" \
  -d '{"query":"年假 报销","project_id":"p1","model":"chat","top_k":3,"min_score":0.99}'
# 期望：prompt 中同文档切片按 chunk_index 升序；邻居块以（上下文）标记；
#       citations 中只出现原命中 chunk_id

# 关闭扩展：--gateway.rag.answer.neighbor-window=0
# 期望：只拼装原命中，顺序仍按 chunk_index 还原
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1 + `records/REQ-5.4.md` §3（4 分钟）
2. §3.1 三条实现要点（2 分钟）
3. 核心文件（4 分钟）：`RagContextAssembler`、`RagPromptBuilder`、
   `RagChunkMapper.xml#selectByDocIdIndexRange`、`RagAnswerServiceImpl`

## 3. 实现要点与参考答案

### 3.1 三条关键实现要点

1. **召回序 ≠ 上下文序**：检索仍按相关度返回；回答侧按 `doc_id` 分组、组内按 `chunk_index` 还原。
2. **邻居回库取**：top-k 之外的邻居只能按租户/项目/文档/序号区间查询；合并区间避免 N+1。
3. **引用与上下文分离**：只有原命中分配 `[n]` 并进入 citations；邻居标（上下文），预算优先给引用。

### 3.2 自测题与参考答案

1. 为什么不在 `/v1/rag/search` 里按 `chunk_index` 排序？
2. 邻居扩展为什么必须带 `tenant_id + project_id`？
3. 预算不足时为什么优先保留引用而不是邻居？
4. 同一文档多个命中窗口重叠时如何避免重复？
5. 邻居查询失败时回答应该失败吗？

### 参考答案

1. 检索接口对外语义是相关度排序；改成原文顺序会破坏 RRF 结果与调用方预期。
2. 防止跨租户/跨项目带出切片，检索层过滤不能替代数据访问层的租户下推。
3. 引用是答案可回溯的前提；邻居只是补充语境，丢了可以，引用丢了不可接受。
4. 以 `chunk_id`（等价于 doc_id + chunk_index）去重，窗口合并后统一排序。
5. 不失败；记 WARN 并只用原命中拼装，扩展是增强不是正确性前提。

### 3.3 亲手改口子（可选）

- 把 `gateway.rag.answer.neighbor-window` 从 1 改成 0，重跑回答集成测试，
  观察 prompt 中不再出现（上下文）块，引用与顺序不变。

## 4. 回归影响

- `/v1/rag/answer` 发给模型的上下文顺序与内容变化；响应字段与 citations 语义不变。
- `/v1/rag/search` 的排序、字段、分页路径不受影响。
- 默认开启扩展会增加每文档一次范围查询；可用配置关闭。

## 5. 验收记录

- 2026-11-17：实现与自检完成，待主人验收。
