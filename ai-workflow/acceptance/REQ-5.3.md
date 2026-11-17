# REQ-5.3 验收：混合检索（向量 + 字面 RRF）

- 状态：待验收（2026-11-17，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-5.3.md`

## 1. 需求验收标准

> 实现前冻结，唯一勾选点。

- [x] 含精确编号/术语的查询，top-k 能召回对应切片（构造真实用例：`SKU-9981`）
- [x] 两路结果融合后的排序有测试覆盖；权重变化影响排序（`RagHybridRetrievalTest`）
- [x] 向量路失败时字面路可用，反之亦然（单元测试覆盖两向降级）
- [x] 关闭开关后与三期行为一致（`RagHybridDisabledIntegrationTest`）
- [x] 租户/项目隔离用例不回退（两路均在召回层下推 + 编排层复核）
- [x] 门禁通过

**验收命令**：

```bash
# 关闭开关（默认开启）
curl -s -X POST localhost:18080/v1/rag/search \
  -H "Authorization: Bearer $VIRTUAL" -H "Content-Type: application/json" \
  -d '{"query":"SKU-9981","project_id":"p1","top_k":3,"min_score":0.99}'
# 期望命中精确编号切片，match_type=keyword/both，keyword_score>0

# 回退验证：启动时加 --gateway.rag.search.hybrid-enabled=false
# 期望 reranked=true、match_type=vector、rrf_score=null
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1 + `records/REQ-5.3.md` §3（4 分钟）
2. §3.1 三条实现要点（2 分钟）
3. 核心文件（4 分钟）：`RagRetrievalCore`、`RagChunkMapper.xml#searchKeyword`、
   `RagSearchHitResDTO`、`RagHybridRetrievalTest`

## 3. 实现要点与参考答案

### 3.1 三条关键实现要点

1. **两路都下推租户/项目**：字面 SQL 直接带 `tenant_id + project_id`，编排层再复核切片字段。
2. **RRF 只用名次**：两路分数量纲不同也能融合；`k` 与两路权重可配，便于调排序。
3. **降级在编排层**：任一路异常只记 WARN，另一路继续；两路都失败才抛出。

### 3.2 自测题与参考答案

1. 为什么 `min_score` 不能作用于字面路？
2. 为什么字面路选 LIKE 而不是上 pg_trgm？
3. 为什么混合模式不再叠加规则重排？
4. 两路都返回同一 chunk 时怎么处理？
5. 开关关闭后为什么要保证完全不调用字面 SQL？

### 参考答案

1. 字面分是词项覆盖率，与向量余弦不可比；套用阈值会误杀精确命中。
2. 不引入扩展、H2 兼容、当前语料量小；性能不足时再评估 trigram + GIN。
3. 三重排序难以解释，验收也难定位；RRF 已覆盖两路融合，规则重排留给回退路径。
4. 按 `chunk_id` 去重，两路名次都计入 RRF，`match_type=both`。
5. 回退语义必须与三期完全一致，包括不产生字面查询的额外开销。

### 3.3 亲手改口子（可选）

- 把 `rrf-keyword-weight` 调成 0，重跑 `RagHybridRetrievalTest`，
  观察排序退化为向量序；再恢复。

## 4. 回归影响

- 默认开启混合检索后，`/v1/rag/search` 的顺序与 `reranked` 字段含义变化；
  原 `min_score`/`rerank` 用例已拆到关闭开关专项类验证。
- `/v1/rag/search` 命中新增 4 个字段（additive），路径与鉴权不变。

## 5. 验收记录

- 2026-11-17：实现与自检完成，待主人验收。
