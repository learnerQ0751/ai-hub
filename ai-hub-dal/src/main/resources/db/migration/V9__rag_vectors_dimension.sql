-- V9：REQ-3.7 embedding 模型目录与多维兼容
-- 向量列固定 1024 维；384/512/768 维模型由应用层零填充后写入（cosine 距离不变）。
-- 向量属于派生数据：切换 embedding 模型后原向量不可复用，删除后由 reindex 重建。
-- 对已有文档：embedding_model 与当前模型不一致时列表返回 reindex_required=true。

DROP INDEX IF EXISTS idx_rag_vectors_hnsw;

DELETE FROM rag_vectors;

ALTER TABLE rag_vectors
    ALTER COLUMN embedding TYPE vector(1024) USING embedding::vector(1024);

COMMENT ON COLUMN rag_vectors.embedding IS
    'pgvector 向量；列固定 1024 维，低维模型零填充，cosine 语义不变';

CREATE INDEX IF NOT EXISTS idx_rag_vectors_hnsw
    ON rag_vectors USING hnsw (embedding vector_cosine_ops);
