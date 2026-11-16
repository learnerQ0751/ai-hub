package com.qtua.aihub.console.controller;

import com.qtua.aihub.api.RagDocumentService;
import com.qtua.aihub.api.model.res.RagDocumentDeleteResDTO;
import com.qtua.aihub.api.model.res.RagDocumentListResDTO;
import com.qtua.aihub.api.model.res.RagDocumentResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * RAG 文档接入入口（数据面，任意有效 Key）。
 *
 * <p>治理由外层 GatewayGovernanceFilter 统一完成：callType=RAG，只做 RPM，
 * 调用轨迹写 call_logs；上传为 multipart，过滤器不缓存请求体。</p>
 */
@RestController
public class RagDocumentController {

    private final RagDocumentService ragDocumentService;

    /**
     * 构造控制器。
     *
     * @param ragDocumentService 文档服务
     */
    public RagDocumentController(RagDocumentService ragDocumentService) {
        this.ragDocumentService = ragDocumentService;
    }

    /**
     * 上传并接入文档。
     *
     * @param file      文件
     * @param projectId 项目 ID
     * @return 201（新建）或 200（命中重复）
     */
    @PostMapping(path = "/v1/rag/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RagDocumentResDTO> upload(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("project_id") String projectId) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw ChatCompletionException.invalidRequest("上传文件读取失败", "file");
        }
        RagDocumentResDTO result = ragDocumentService.ingest(projectId,
                file.getOriginalFilename(), file.getContentType(), content);
        HttpStatus status = result.duplicated() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * 按项目查询文档列表。
     *
     * @param projectId 项目 ID
     * @param limit     返回条数（可选）
     * @return 文档列表
     */
    @GetMapping(path = "/v1/rag/documents", produces = MediaType.APPLICATION_JSON_VALUE)
    public RagDocumentListResDTO list(@RequestParam("project_id") String projectId,
                                      @RequestParam(value = "limit", required = false)
                                      Integer limit) {
        return ragDocumentService.list(projectId, limit);
    }

    /**
     * 重建文档索引（先删旧向量，再按当前模型向量化）。
     *
     * @param docId     文档 ID
     * @param projectId 项目 ID
     * @return 索引后的文档
     */
    @PostMapping(path = "/v1/rag/documents/{docId}/reindex",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RagDocumentResDTO reindex(@PathVariable("docId") String docId,
                                     @RequestParam("project_id") String projectId) {
        return ragDocumentService.reindex(docId, projectId);
    }

    /**
     * 删除文档并级联切片与向量。
     *
     * @param docId     文档 ID
     * @param projectId 项目 ID
     * @return 删除结果
     */
    @DeleteMapping(path = "/v1/rag/documents/{docId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RagDocumentDeleteResDTO remove(@PathVariable("docId") String docId,
                                          @RequestParam("project_id") String projectId) {
        return ragDocumentService.remove(docId, projectId);
    }
}
