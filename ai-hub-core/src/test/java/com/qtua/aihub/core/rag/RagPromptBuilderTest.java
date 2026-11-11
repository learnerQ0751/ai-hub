package com.qtua.aihub.core.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.4 prompt 构造测试：引用编号、上下文预算、自由回答与 score 选择。
 */
class RagPromptBuilderTest {

    private final RagPromptBuilder builder = new RagPromptBuilder();

    @Test
    void builds_numbered_context_and_citations_in_order() {
        RagAnswerPrompt prompt = builder.build("年假有多少天",
                List.of(chunk("c1", "d1", "员工手册.md", "年假为 5 天", 0.8, null),
                        chunk("c2", "d2", "考勤制度.md", "年假需提前申请", 0.7, null)),
                1000, false);

        assertThat(prompt.systemPrompt()).contains("参考资料").contains("[1]");
        assertThat(prompt.userPrompt()).contains("[1]（来源：员工手册.md）")
                .contains("[2]（来源：考勤制度.md）")
                .contains("年假为 5 天")
                .contains("问题：年假有多少天");
        assertThat(prompt.citations()).extracting(RagAnswerCitation::index)
                .containsExactly(1, 2);
        assertThat(prompt.citations()).extracting(RagAnswerCitation::chunkId)
                .containsExactly("c1", "c2");
    }

    @Test
    void truncates_context_and_citations_to_budget() {
        RagAnswerPrompt prompt = builder.build("问题",
                List.of(chunk("c1", "d1", "a.md", "甲".repeat(100), 0.8, null),
                        chunk("c2", "d2", "b.md", "乙".repeat(100), 0.7, null)),
                60, false);

        assertThat(prompt.citations()).extracting(RagAnswerCitation::chunkId)
                .containsExactly("c1");
        assertThat(prompt.userPrompt()).contains("[1]").doesNotContain("[2]");
    }

    @Test
    void free_answer_prompt_has_no_citations() {
        RagAnswerPrompt prompt = builder.build("火星基地建设", List.of(), 1000, true);

        assertThat(prompt.systemPrompt()).contains("通用知识");
        assertThat(prompt.userPrompt()).contains("问题：火星基地建设");
        assertThat(prompt.citations()).isEmpty();
    }

    @Test
    void citation_score_prefers_rerank_score() {
        RagAnswerPrompt prompt = builder.build("问题",
                List.of(chunk("c1", "d1", "a.md", "内容", 0.5, 0.9)), 1000, false);

        assertThat(prompt.citations().get(0).score()).isEqualTo(0.9);
    }

    @Test
    void grounded_prompt_without_hits_still_returns_empty_citations() {
        RagAnswerPrompt prompt = builder.build("问题", List.of(), 1000, false);

        assertThat(prompt.citations()).isEmpty();
        assertThat(prompt.userPrompt()).contains("问题：问题");
    }

    private RagRetrievedChunk chunk(String chunkId, String docId, String source, String content,
                                    double score, Double rerankScore) {
        return new RagRetrievedChunk(chunkId, docId, "p1", source, 0, content, score,
                rerankScore);
    }
}
