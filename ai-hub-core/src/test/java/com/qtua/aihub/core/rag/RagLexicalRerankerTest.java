package com.qtua.aihub.core.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.3 规则重排测试：词项提取、覆盖率加权排序、权重收敛与无词项保持原序。
 */
class RagLexicalRerankerTest {

    private final RagLexicalReranker reranker = new RagLexicalReranker();

    @Test
    void terms_splits_chinese_bigrams_and_english_words() {
        Set<String> terms = reranker.terms("年假 报销 Policy-2024");

        assertThat(terms).containsExactlyInAnyOrder("年假", "报销", "policy", "2024");
    }

    @Test
    void rerank_prefers_chunk_with_higher_query_coverage() {
        RagRetrievedChunk report = chunk("c1", "报销流程说明", 0.6);
        RagRetrievedChunk leave = chunk("c2", "年假与报销制度", 0.55);

        List<RagRetrievedChunk> reranked = reranker.rerank("年假 报销",
                List.of(report, leave), 0.5);

        assertThat(reranked).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c2", "c1");
        assertThat(reranked.get(0).rerankScore()).isGreaterThan(reranked.get(1).rerankScore());
    }

    @Test
    void rerank_keeps_order_when_query_has_no_terms() {
        RagRetrievedChunk first = chunk("c1", "任意内容", 0.8);
        RagRetrievedChunk second = chunk("c2", "其他内容", 0.7);

        List<RagRetrievedChunk> reranked = reranker.rerank("？？", List.of(first, second), 0.5);

        assertThat(reranked).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c1", "c2");
        assertThat(reranked).extracting(RagRetrievedChunk::rerankScore)
                .containsExactly(null, null);
    }

    @Test
    void rerank_clamps_weight_to_unit_interval() {
        RagRetrievedChunk lowCoverage = chunk("c1", "报销", 0.9);
        RagRetrievedChunk highCoverage = chunk("c2", "年假 报销", 0.1);

        List<RagRetrievedChunk> coverageFirst = reranker.rerank("年假 报销",
                List.of(lowCoverage, highCoverage), 2.0);
        List<RagRetrievedChunk> vectorFirst = reranker.rerank("年假 报销",
                List.of(lowCoverage, highCoverage), -1.0);

        assertThat(coverageFirst).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c2", "c1");
        assertThat(vectorFirst).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c1", "c2");
    }

    private RagRetrievedChunk chunk(String chunkId, String content, double score) {
        return new RagRetrievedChunk(chunkId, "doc-1", "project-a", "demo.md", 0, content,
                score, null);
    }
}
