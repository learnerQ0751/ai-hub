package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * RAG 检索元数据过滤条件（HTTP 契约）。
 *
 * <p>字段 snake_case 与 RAG MCP 契约保持一致；两个条件同时存在时取交集。</p>
 *
 * @param docIds 允许命中的文档 ID；null/空表示不限
 * @param source 允许命中的来源文件名；null/空白表示不限
 */
public record RagSearchFilterDTO(
        @JsonProperty("doc_ids") List<String> docIds,
        @JsonProperty("source") String source) {
}
