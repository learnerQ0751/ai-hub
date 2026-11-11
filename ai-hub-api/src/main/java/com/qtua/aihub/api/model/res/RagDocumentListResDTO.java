package com.qtua.aihub.api.model.res;

import java.util.List;
import java.util.Objects;

/**
 * RAG 文档列表视图。
 *
 * @param documents 文档列表；无数据为空列表
 * @param count     条数
 */
public record RagDocumentListResDTO(List<RagDocumentResDTO> documents, int count) {

    /**
     * 防御性复制列表；无数据返回空列表。
     */
    public RagDocumentListResDTO {
        documents = Objects.isNull(documents) ? List.of() : List.copyOf(documents);
    }
}
