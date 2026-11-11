package com.qtua.aihub.core.rag;

import java.util.List;
import java.util.Objects;

/**
 * 一次 RAG 生成使用的 prompt 与实际引用。
 *
 * <p>citations 只包含真正写入 prompt 的切片，编号从 1 连续；生成的答案按这些编号引用。</p>
 *
 * @param systemPrompt 系统提示词
 * @param userPrompt   用户提示词（含编号资料与问题）
 * @param citations    实际使用的引用
 */
public record RagAnswerPrompt(String systemPrompt, String userPrompt,
                              List<RagAnswerCitation> citations) {

    /**
     * 防御性复制引用列表；无引用为空列表。
     */
    public RagAnswerPrompt {
        citations = Objects.isNull(citations) ? List.of() : List.copyOf(citations);
    }
}
