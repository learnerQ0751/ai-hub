package com.qtua.aihub.api;

import com.qtua.aihub.api.model.res.RagDocumentDeleteResDTO;
import com.qtua.aihub.api.model.res.RagDocumentListResDTO;
import com.qtua.aihub.api.model.res.RagDocumentResDTO;

/**
 * RAG 文档接入服务：上传解析、切分、列表与删除。
 *
 * <p>所有操作强制携带 project_id；删除在事务内级联切片与向量。
 * 文件大小、类型与去重策略由服务端配置控制。</p>
 */
public interface RagDocumentService {

    /**
     * 上传并接入文档。
     *
     * @param projectId   项目 ID
     * @param fileName    原始文件名
     * @param contentType 内容类型
     * @param content     文件字节
     * @return 文档视图（含是否命中重复）
     */
    RagDocumentResDTO ingest(String projectId, String fileName, String contentType, byte[] content);

    /**
     * 按项目查询文档列表。
     *
     * @param projectId 项目 ID
     * @param limit     返回条数（可选，服务端收敛）
     * @return 文档列表
     */
    RagDocumentListResDTO list(String projectId, Integer limit);

    /**
     * 删除文档并级联切片与向量。
     *
     * @param docId     文档 ID
     * @param projectId 项目 ID
     * @return 删除结果
     */
    RagDocumentDeleteResDTO remove(String docId, String projectId);
}
