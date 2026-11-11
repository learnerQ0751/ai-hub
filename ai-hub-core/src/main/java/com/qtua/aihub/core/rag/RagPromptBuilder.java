package com.qtua.aihub.core.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RAG prompt 构造。
 *
 * <p>有召回时把命中按顺序编号为 `[1][2]...`，每段带来源文件名；上下文总长度受
 * `max-context-chars` 约束，超预算时按顺序少放引用（至少保留第一条）。引用列表与 prompt
 * 同序生成，保证编号与 citations 强对应。无召回且允许自由回答时生成不含引用的通用提示词。</p>
 */
@Component
public class RagPromptBuilder {

    private static final int MIN_CONTEXT_CHARS = 1;
    private static final String UNKNOWN_SOURCE = "未知来源";

    private static final String GROUNDED_SYSTEM_PROMPT = "你是企业知识库问答助手。"
            + "请严格依据“参考资料”回答用户问题，不要编造资料或引用不存在的编号。\n"
            + "引用要求：\n"
            + "1. 只能引用参考资料中出现的编号，格式为 [1]、[2]；\n"
            + "2. 每个关键结论后标注来源编号；\n"
            + "3. 如果参考资料不足以回答，请直接说明“资料不足，无法回答”，不要编造。";

    private static final String FREE_SYSTEM_PROMPT = "你是企业知识库问答助手。"
            + "当前知识库没有检索到相关资料。请基于通用知识谨慎回答，"
            + "并明确说明这是通用建议、未引用企业内部资料；不要虚构引用编号。";

    /**
     * 构造 prompt。
     *
     * @param query           用户问题
     * @param hits            检索命中（按最终排序）
     * @param maxContextChars 资料部分最大字符数
     * @param freeAnswer      无召回时是否允许自由回答
     * @return prompt 与实际引用
     */
    public RagAnswerPrompt build(String query, List<RagRetrievedChunk> hits,
                                 int maxContextChars, boolean freeAnswer) {
        if (freeAnswer) {
            return new RagAnswerPrompt(FREE_SYSTEM_PROMPT, "问题：" + query + "\n", List.of());
        }
        int budget = Math.max(MIN_CONTEXT_CHARS, maxContextChars);
        StringBuilder context = new StringBuilder();
        List<RagAnswerCitation> citations = new ArrayList<>();
        for (RagRetrievedChunk hit : hits) {
            int index = citations.size() + 1;
            String block = "[" + index + "]（来源：" + sourceOf(hit) + "）\n"
                    + hit.content() + "\n\n";
            int remaining = budget - context.length();
            if (remaining <= 0 && !citations.isEmpty()) {
                break;
            }
            if (block.length() > remaining) {
                if (!citations.isEmpty()) {
                    break;
                }
                // 至少保留第一条：按预算截断，避免“有引用但 prompt 里没有资料”。
                block = block.substring(0, Math.max(1, Math.min(block.length(), remaining)));
            }
            context.append(block);
            citations.add(new RagAnswerCitation(index, hit.docId(), hit.chunkId(),
                    sourceOf(hit), scoreOf(hit)));
        }
        String userPrompt = "参考资料：\n" + context + "\n问题：" + query + "\n";
        return new RagAnswerPrompt(GROUNDED_SYSTEM_PROMPT, userPrompt, citations);
    }

    private String sourceOf(RagRetrievedChunk hit) {
        return Objects.isNull(hit.source()) ? UNKNOWN_SOURCE : hit.source();
    }

    private double scoreOf(RagRetrievedChunk hit) {
        return Objects.isNull(hit.rerankScore()) ? hit.score() : hit.rerankScore();
    }
}
