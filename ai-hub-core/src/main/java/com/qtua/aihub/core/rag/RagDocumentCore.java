package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.rag.RagChunkDraft;
import com.qtua.aihub.common.rag.RagIndexStatus;
import com.qtua.aihub.common.rag.VectorStore;
import com.qtua.aihub.integration.rag.DocumentParseException;
import com.qtua.aihub.integration.rag.DocumentTextExtractor;
import com.qtua.aihub.manager.model.RagChunkBO;
import com.qtua.aihub.manager.model.RagDocumentBO;
import com.qtua.aihub.manager.rag.RagChunkManager;
import com.qtua.aihub.manager.rag.RagDocumentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * RAG 文档接入编排。
 *
 * <p>去重 → 解析 → 切分 → 落库。解析失败时持久化 FAILED 文档与原始文件后抛出
 * {@link DocumentParseException}，由 Service 转成 400；删除在事务内级联切片（及向量）。
 * 本类无可变业务状态，多实例安全。</p>
 */
@Component
public class RagDocumentCore {

    private static final Logger LOG = LoggerFactory.getLogger(RagDocumentCore.class);

    private static final String CHUNK_INDEX_FORMAT = "%04d";
    private static final int MAX_ERROR_LENGTH = 1024;

    private final RagDocumentManager documentManager;
    private final RagChunkManager chunkManager;
    private final RagChunker chunker;
    private final DocumentTextExtractor textExtractor;
    private final RagIndexCore indexCore;
    private final VectorStore vectorStore;
    private final RagProperties properties;

    /**
     * 构造编排。
     *
     * @param documentManager 文档门面
     * @param chunkManager    切片门面
     * @param chunker         切分器
     * @param textExtractor   文本提取器
     * @param indexCore       向量化编排
     * @param vectorStore     向量存储（删除级联）
     * @param properties      RAG 配置
     */
    public RagDocumentCore(RagDocumentManager documentManager, RagChunkManager chunkManager,
                           RagChunker chunker, DocumentTextExtractor textExtractor,
                           RagIndexCore indexCore, VectorStore vectorStore,
                           RagProperties properties) {
        this.documentManager = documentManager;
        this.chunkManager = chunkManager;
        this.chunker = chunker;
        this.textExtractor = textExtractor;
        this.indexCore = indexCore;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * 接入文档。
     *
     * @param projectId   项目 ID
     * @param source      文件名
     * @param contentType 内容类型
     * @param content     文件字节
     * @param override    是否覆盖重复文档
     * @return 接入结果
     */
    public RagIngestResult ingest(String projectId, String source, String contentType,
                                  byte[] content, boolean override) {
        String contentHash = sha256(content);
        Optional<RagDocumentBO> existing = documentManager.findByProjectAndHash(projectId,
                contentHash);
        if (existing.isPresent()) {
            RagDocumentBO document = existing.get();
            if (!override && !RagIndexStatus.FAILED.name().equals(document.indexStatus())) {
                return new RagIngestResult(document, true, false);
            }
            deleteDocument(document.docId());
        }
        String text;
        try {
            text = textExtractor.extract(source, content);
        } catch (DocumentParseException ex) {
            RagDocumentBO failed = persistDocument(projectId, source, contentType, content,
                    contentHash, List.of(), RagIndexStatus.FAILED, ex.getMessage());
            LOG.warn("rag document parse failed: docId={}, source={}, size={}",
                    failed.docId(), source, content.length);
            throw ex;
        }
        List<RagChunkDraft> drafts = chunker.split(text);
        RagDocumentBO document = persistDocument(projectId, source, contentType, content,
                contentHash, drafts, RagIndexStatus.PENDING, null);
        document = indexCore.index(document.docId());
        LOG.info("rag document ingested: docId={}, projectId={}, source={}, size={}, chunks={}, "
                        + "status={}",
                document.docId(), projectId, source, content.length, drafts.size(),
                document.indexStatus());
        return new RagIngestResult(document, false, existing.isPresent());
    }

    private RagDocumentBO persistDocument(String projectId, String source, String contentType,
                                          byte[] content, String contentHash,
                                          List<RagChunkDraft> drafts, RagIndexStatus status,
                                          String errorMessage) {
        Instant now = Instant.now();
        String docId = UUID.randomUUID().toString();
        RagDocumentBO document = new RagDocumentBO(docId, projectId, source, contentType,
                content.length, contentHash, content, drafts.size(), status.name(), null, null,
                null, BigDecimal.ZERO, truncate(errorMessage), now, now);
        try {
            documentManager.insert(document);
        } catch (DataIntegrityViolationException ex) {
            // 并发上传同一文件：唯一约束保证只有一个文档，冲突方返回已有文档。
            return documentManager.findByProjectAndHash(projectId, contentHash)
                    .orElseThrow(() -> ex);
        }
        saveChunks(document, drafts, now);
        return documentManager.findByDocId(docId).orElse(document);
    }

    private void saveChunks(RagDocumentBO document, List<RagChunkDraft> drafts, Instant now) {
        if (drafts.isEmpty()) {
            return;
        }
        List<RagChunkBO> chunks = drafts.stream()
                .map(draft -> new RagChunkBO(chunkId(document.docId(), draft.chunkIndex()),
                        document.docId(), document.projectId(), draft.chunkIndex(),
                        draft.content(), draft.charCount(), document.source(),
                        RagIndexStatus.PENDING.name(), null, null, null, now))
                .toList();
        chunkManager.insertAll(chunks, properties.getChunk().getPersistBatchSize());
    }

    /**
     * 查询文档（校验项目归属）。
     *
     * @param docId     文档 ID
     * @param projectId 项目 ID
     * @return 文档；不存在或跨项目为空
     */
    public Optional<RagDocumentBO> find(String docId, String projectId) {
        return documentManager.findByDocId(docId)
                .filter(document -> Objects.equals(document.projectId(), projectId));
    }

    /**
     * 按项目查询文档列表。
     *
     * @param projectId 项目 ID
     * @param limit     返回条数
     * @return 文档列表
     */
    public List<RagDocumentBO> list(String projectId, int limit) {
        return documentManager.listByProject(projectId, limit);
    }

    /**
     * 删除文档并级联切片（及向量）。
     *
     * @param docId 文档 ID
     * @return 删除结果
     */
    @Transactional(rollbackFor = Throwable.class)
    public RagDeleteResult delete(String docId) {
        int vectors = vectorStore.deleteByDocument(docId);
        int chunks = chunkManager.deleteByDocId(docId);
        int deleted = documentManager.deleteByDocId(docId);
        return new RagDeleteResult(deleted > 0, chunks, vectors);
    }

    /**
     * 重建文档索引（先删旧向量，再按当前模型向量化）。
     *
     * @param docId 文档 ID
     * @return 索引后的文档；不存在为空
     */
    public Optional<RagDocumentBO> reindex(String docId) {
        return Optional.ofNullable(indexCore.reindex(docId));
    }

    /**
     * 删除文档（内部去重/覆盖使用，不校验项目）。
     *
     * @param docId 文档 ID
     */
    @Transactional(rollbackFor = Throwable.class)
    public void deleteDocument(String docId) {
        vectorStore.deleteByDocument(docId);
        chunkManager.deleteByDocId(docId);
        documentManager.deleteByDocId(docId);
    }

    /**
     * 计算文件 SHA-256。
     *
     * @param content 文件字节
     * @return 十六进制 hash
     */
    public String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * 生成确定性切片 ID。
     *
     * @param docId      文档 ID
     * @param chunkIndex 序号
     * @return 切片 ID
     */
    public String chunkId(String docId, int chunkIndex) {
        return docId + "-" + String.format(CHUNK_INDEX_FORMAT, chunkIndex);
    }

    private String truncate(String message) {
        if (Objects.isNull(message)) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH)
                : message;
    }
}
