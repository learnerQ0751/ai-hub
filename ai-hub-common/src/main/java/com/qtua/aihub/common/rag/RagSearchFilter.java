package com.qtua.aihub.common.rag;

import java.util.List;
import java.util.Objects;

/**
 * RAG 检索元数据过滤条件。
 *
 * <p>只支持可下推到切片字段的两个维度：文档 ID 集合与来源文件名。空条件表示不过滤；
 * 列表在构造时防御性复制，可作为不可变值对象跨线程复用。</p>
 *
 * @param docIds 允许命中的文档 ID；空表示不限
 * @param source 允许命中的来源文件名；null/空白表示不限
 */
public record RagSearchFilter(List<String> docIds, String source) {

    /**
     * 校验并复制过滤条件。
     */
    public RagSearchFilter {
        docIds = Objects.isNull(docIds) ? List.of() : List.copyOf(docIds);
        source = normalize(source);
    }

    /**
     * 空过滤条件。
     *
     * @return 不做任何过滤的条件
     */
    public static RagSearchFilter empty() {
        return new RagSearchFilter(List.of(), null);
    }

    /**
     * 是否没有过滤条件。
     *
     * @return true 表示不过滤
     */
    public boolean isEmpty() {
        return docIds.isEmpty() && Objects.isNull(source);
    }

    /**
     * 判断切片是否满足过滤条件。
     *
     * @param docId       文档 ID
     * @param chunkSource 切片来源文件名
     * @return true 表示命中
     */
    public boolean matches(String docId, String chunkSource) {
        if (!docIds.isEmpty() && !docIds.contains(docId)) {
            return false;
        }
        return Objects.isNull(source) || Objects.equals(source, chunkSource);
    }

    private static String normalize(String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
