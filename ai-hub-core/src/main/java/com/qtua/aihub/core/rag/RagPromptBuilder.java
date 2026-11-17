package com.qtua.aihub.core.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RAG prompt 构造。
 *
 * <p>引用片段按 `[1][2]...` 编号并带来源文件名；邻接扩展片段标记为「（上下文）」，
 * 不分配编号、不进入 citations（REQ-5.4）。上下文总长度受 `max-context-chars` 约束：
 * 预算优先给引用，剩余预算按顺序填充邻居，至少保留第一条引用。
 * 无召回且允许自由回答时生成不含引用的通用提示词。</p>
 */
@Component
public class RagPromptBuilder {

    private static final int MIN_CONTEXT_CHARS = 1;
    private static final String UNKNOWN_SOURCE = "未知来源";
    private static final String CONTEXT_PREFIX = "（上下文）来源：";

    private static final String GROUNDED_SYSTEM_PROMPT = "你是企业知识库问答助手。"
            + "请严格依据“参考资料”回答用户问题，不要编造资料或引用不存在的编号。\n"
            + "引用要求：\n"
            + "1. 只能引用参考资料中出现的编号，格式为 [1]、[2]；\n"
            + "2. 每个关键结论后标注来源编号；\n"
            + "3. 标有“（上下文）”的片段仅用于理解语义，不得作为引用来源；\n"
            + "4. 如果参考资料不足以回答，请直接说明“资料不足，无法回答”，不要编造。";

    private static final String FREE_SYSTEM_PROMPT = "你是企业知识库问答助手。"
            + "当前知识库没有检索到相关资料。请基于通用知识谨慎回答，"
            + "并明确说明这是通用建议、未引用企业内部资料；不要虚构引用编号。";

    /**
     * 构造 prompt（仅检索命中，无邻居扩展）。
     *
     * @param query           用户问题
     * @param hits            检索命中（按最终排序）
     * @param maxContextChars 资料部分最大字符数
     * @param freeAnswer      无召回时是否允许自由回答
     * @return prompt 与实际引用
     */
    public RagAnswerPrompt build(String query, List<RagRetrievedChunk> hits,
                                 int maxContextChars, boolean freeAnswer) {
        List<RagContextSegment> segments = Objects.isNull(hits) ? List.of()
                : hits.stream().map(RagContextSegment::fromHit).toList();
        return buildFromSegments(query, segments, maxContextChars, freeAnswer);
    }

    /**
     * 构造 prompt（含上下文片段）。
     *
     * @param query           用户问题
     * @param segments        按文档与序号排好的片段（引用在前排序中保留原文顺序）
     * @param maxContextChars 资料部分最大字符数
     * @param freeAnswer      无召回时是否允许自由回答
     * @return prompt 与实际引用
     */
    public RagAnswerPrompt buildFromSegments(String query, List<RagContextSegment> segments,
                                             int maxContextChars, boolean freeAnswer) {
        if (freeAnswer) {
            return new RagAnswerPrompt(FREE_SYSTEM_PROMPT, "问题：" + query + "\n", List.of());
        }
        List<RagContextSegment> safe = Objects.isNull(segments) ? List.of() : segments;
        int budget = Math.max(MIN_CONTEXT_CHARS, maxContextChars);
        Selection selection = selectWithinBudget(safe, budget);
        StringBuilder context = new StringBuilder();
        List<RagAnswerCitation> citations = new ArrayList<>();
        for (int index = 0; index < safe.size(); index++) {
            if (!selection.selected()[index]) {
                continue;
            }
            RagContextSegment segment = safe.get(index);
            if (segment.citation()) {
                int citationIndex = citations.size() + 1;
                context.append(truncate(citationBlock(citationIndex, segment),
                        selection.keepLengths()[index]));
                citations.add(new RagAnswerCitation(citationIndex, segment.docId(),
                        segment.chunkId(), sourceOf(segment), segment.score()));
            } else {
                context.append(truncate(contextBlock(segment), selection.keepLengths()[index]));
            }
        }
        String userPrompt = "参考资料：\n" + context + "\n问题：" + query + "\n";
        return new RagAnswerPrompt(GROUNDED_SYSTEM_PROMPT, userPrompt, citations);
    }

    /**
     * 预算分配：先保证引用（至少第一条），再用剩余预算按顺序放入邻居。
     */
    private Selection selectWithinBudget(List<RagContextSegment> segments, int budget) {
        boolean[] selected = new boolean[segments.size()];
        int[] keepLengths = new int[segments.size()];
        for (int index = 0; index < keepLengths.length; index++) {
            keepLengths[index] = Integer.MAX_VALUE;
        }
        int used = 0;
        int citationCount = 0;
        for (int index = 0; index < segments.size(); index++) {
            RagContextSegment segment = segments.get(index);
            if (!segment.citation()) {
                continue;
            }
            String block = citationBlock(citationCount + 1, segment);
            int remaining = budget - used;
            if (block.length() <= remaining) {
                selected[index] = true;
                used += block.length();
                citationCount++;
                continue;
            }
            if (citationCount == 0) {
                // 至少保留第一条引用：按预算截断，避免“有引用但 prompt 里没有资料”。
                int keep = Math.max(1, Math.min(block.length(), remaining));
                selected[index] = true;
                keepLengths[index] = keep;
                used += keep;
                citationCount++;
            }
            break;
        }
        for (int index = 0; index < segments.size(); index++) {
            RagContextSegment segment = segments.get(index);
            if (selected[index] || segment.citation()) {
                continue;
            }
            String block = contextBlock(segment);
            int remaining = budget - used;
            if (block.length() > remaining) {
                break;
            }
            selected[index] = true;
            used += block.length();
        }
        return new Selection(selected, keepLengths);
    }

    private String truncate(String block, int keepLength) {
        return block.length() <= keepLength ? block : block.substring(0, Math.max(1, keepLength));
    }

    private String citationBlock(int index, RagContextSegment segment) {
        return "[" + index + "]（来源：" + sourceOf(segment) + "）\n"
                + segment.content() + "\n\n";
    }

    private String contextBlock(RagContextSegment segment) {
        return CONTEXT_PREFIX + sourceOf(segment) + "\n" + segment.content() + "\n\n";
    }

    private String sourceOf(RagContextSegment segment) {
        return Objects.isNull(segment.source()) ? UNKNOWN_SOURCE : segment.source();
    }

    /**
     * 预算分配结果：选中标记与需要截断的长度。
     */
    private record Selection(boolean[] selected, int[] keepLengths) {
    }
}
