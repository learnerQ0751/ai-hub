package com.qtua.aihub.service;

import com.qtua.aihub.api.RagDocumentService;
import com.qtua.aihub.api.model.res.RagDocumentDeleteResDTO;
import com.qtua.aihub.api.model.res.RagDocumentListResDTO;
import com.qtua.aihub.api.model.res.RagDocumentResDTO;
import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.core.rag.RagDeleteResult;
import com.qtua.aihub.core.rag.RagDocumentCore;
import com.qtua.aihub.core.rag.RagIngestResult;
import com.qtua.aihub.integration.rag.DocumentParseException;
import com.qtua.aihub.manager.model.RagDocumentBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * RAG 文档服务实现。
 *
 * <p>职责：参数校验、去重配置读取、DTO↔BO 转换；解析/切分/存储编排在
 * {@link RagDocumentCore}。日志只记录 id、大小、切片数，不记录正文。</p>
 */
@Service
public class RagDocumentServiceImpl implements RagDocumentService {

    private static final Logger LOG = LoggerFactory.getLogger(RagDocumentServiceImpl.class);

    private static final int MAX_PROJECT_LENGTH = 64;
    private static final int MAX_SOURCE_LENGTH = 512;
    private static final String PARAM_PROJECT = "project_id";
    private static final String PARAM_FILE = "file";
    private static final String PARAM_LIMIT = "limit";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final RagDocumentCore documentCore;
    private final EmbeddingModel embeddingModel;
    private final RagProperties properties;

    /**
     * 构造服务。
     *
     * @param documentCore   文档编排
     * @param embeddingModel 当前向量模型（用于判断是否需要重建索引）
     * @param properties     RAG 配置
     */
    public RagDocumentServiceImpl(RagDocumentCore documentCore, EmbeddingModel embeddingModel,
                                  RagProperties properties) {
        this.documentCore = documentCore;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    /**
     * 上传并接入文档。
     *
     * @param projectId   项目 ID
     * @param fileName    原始文件名
     * @param contentType 内容类型
     * @param content     文件字节
     * @return 文档视图
     */
    @Override
    public RagDocumentResDTO ingest(String projectId, String fileName, String contentType,
                                    byte[] content) {
        String normalizedProject = requireProject(projectId);
        String source = normalizeSource(fileName);
        byte[] bytes = requireContent(content, source);
        try {
            RagIngestResult result = documentCore.ingest(normalizedProject, source,
                    resolveContentType(source, contentType), bytes,
                    properties.getDedup().isOverride());
            RagDocumentBO document = documentCore.find(result.document().docId(),
                    normalizedProject).orElse(result.document());
            LOG.info("rag ingest completed: docId={}, projectId={}, source={}, size={}, "
                            + "chunks={}, duplicated={}",
                    document.docId(), normalizedProject, source, bytes.length,
                    document.chunkCount(), result.duplicated());
            return toResDTO(document, result.duplicated());
        } catch (DocumentParseException ex) {
            throw ChatCompletionException.invalidRequest(ex.getMessage(), PARAM_FILE);
        }
    }

    /**
     * 按项目查询文档列表。
     *
     * @param projectId 项目 ID
     * @param limit     返回条数
     * @return 文档列表
     */
    @Override
    public RagDocumentListResDTO list(String projectId, Integer limit) {
        String normalizedProject = requireProject(projectId);
        int resolvedLimit = resolveLimit(limit);
        List<RagDocumentResDTO> documents = documentCore.list(normalizedProject, resolvedLimit)
                .stream()
                .map(document -> toResDTO(document, false))
                .toList();
        return new RagDocumentListResDTO(documents, documents.size());
    }

    /**
     * 重建文档索引。
     *
     * @param docId     文档 ID
     * @param projectId 项目 ID
     * @return 索引后的文档视图
     */
    @Override
    public RagDocumentResDTO reindex(String docId, String projectId) {
        String normalizedProject = requireProject(projectId);
        String normalizedDocId = requireDocId(docId);
        documentCore.find(normalizedDocId, normalizedProject)
                .orElseThrow(() -> ChatCompletionException.ragDocumentNotFound(docId));
        RagDocumentBO document = documentCore.reindex(normalizedDocId)
                .orElseThrow(() -> ChatCompletionException.ragDocumentNotFound(docId));
        LOG.info("rag reindex completed: docId={}, projectId={}, status={}",
                document.docId(), normalizedProject, document.indexStatus());
        return toResDTO(document, false);
    }

    /**
     * 删除文档并级联切片与向量。
     *
     * @param docId     文档 ID
     * @param projectId 项目 ID
     * @return 删除结果
     */
    @Override
    public RagDocumentDeleteResDTO remove(String docId, String projectId) {
        String normalizedProject = requireProject(projectId);
        if (!StringUtils.hasText(docId)) {
            throw ChatCompletionException.invalidRequest("doc_id 不能为空", "doc_id");
        }
        documentCore.find(docId.trim(), normalizedProject)
                .orElseThrow(() -> ChatCompletionException.ragDocumentNotFound(docId));
        RagDeleteResult result = documentCore.delete(docId.trim());
        LOG.info("rag document removed: docId={}, projectId={}, chunksDeleted={}, "
                        + "vectorsDeleted={}",
                docId, normalizedProject, result.chunksDeleted(), result.vectorsDeleted());
        return new RagDocumentDeleteResDTO(result.deleted(), result.chunksDeleted(),
                result.vectorsDeleted());
    }

    private String requireProject(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw ChatCompletionException.invalidRequest("project_id 不能为空", PARAM_PROJECT);
        }
        String trimmed = projectId.trim();
        if (trimmed.length() > MAX_PROJECT_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    "project_id 长度不能超过 " + MAX_PROJECT_LENGTH + " 个字符", PARAM_PROJECT);
        }
        return trimmed;
    }

    private String requireDocId(String docId) {
        if (!StringUtils.hasText(docId)) {
            throw ChatCompletionException.invalidRequest("doc_id 不能为空", "doc_id");
        }
        return docId.trim();
    }

    private String normalizeSource(String fileName) {
        String source = StringUtils.getFilename(fileName);
        if (!StringUtils.hasText(source)) {
            throw ChatCompletionException.invalidRequest("上传文件不能为空", PARAM_FILE);
        }
        if (source.length() > MAX_SOURCE_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    "文件名长度不能超过 " + MAX_SOURCE_LENGTH + " 个字符", PARAM_FILE);
        }
        return source;
    }

    private byte[] requireContent(byte[] content, String source) {
        if (ObjectUtils.isEmpty(content)) {
            throw ChatCompletionException.invalidRequest("上传文件不能为空", PARAM_FILE);
        }
        int limit = resolveMaxSize(source);
        if (content.length > limit) {
            throw ChatCompletionException.invalidRequest(
                    "文件大小不能超过 " + limit + " 字节（扩展名 " + extensionOf(source) + "）",
                    PARAM_FILE);
        }
        return content;
    }

    /**
     * 按扩展名解析上传上限；未命中类型回退默认上限。
     *
     * @param source 文件名
     * @return 最大字节数
     */
    private int resolveMaxSize(String source) {
        String extension = extensionOf(source);
        Integer limit = properties.getUpload().getTypeLimits().get(extension);
        return ObjectUtils.isEmpty(limit) || limit <= 0
                ? properties.getUpload().getMaxSizeBytes() : limit;
    }

    private String extensionOf(String fileName) {
        String name = StringUtils.getFilename(fileName);
        if (!StringUtils.hasText(name) || name.lastIndexOf('.') < 0) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private int resolveLimit(Integer limit) {
        if (ObjectUtils.isEmpty(limit)) {
            return properties.getList().getDefaultLimit();
        }
        if (limit <= 0) {
            throw ChatCompletionException.invalidRequest("limit 必须大于 0", PARAM_LIMIT);
        }
        return Math.min(limit, properties.getList().getMaxLimit());
    }

    private String resolveContentType(String source, String contentType) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        String lower = source.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        return DEFAULT_CONTENT_TYPE;
    }

    private RagDocumentResDTO toResDTO(RagDocumentBO document, boolean duplicated) {
        boolean modelMismatch = StringUtils.hasText(document.embeddingModel())
                && !document.embeddingModel().equals(embeddingModel.modelId());
        boolean dimensionMismatch = !ObjectUtils.isEmpty(document.embeddingDimension())
                && document.embeddingDimension() != embeddingModel.dimension();
        boolean reindexRequired = modelMismatch || dimensionMismatch;
        return new RagDocumentResDTO(document.docId(), document.projectId(), document.source(),
                document.contentType(), document.sizeBytes(), document.contentHash(),
                document.chunkCount(), document.indexStatus(), document.embeddingModel(),
                document.embeddingDimension(), document.embeddingDurationMs(),
                document.embeddingCost(), document.errorMessage(), duplicated, reindexRequired,
                document.createdAt(), document.updatedAt());
    }
}
