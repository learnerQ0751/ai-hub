package com.qtua.aihub.core.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 规则重排：向量分与查询词项覆盖率的加权组合。
 *
 * <p>不做额外模型调用，可解释、可离线测试。词项提取规则：
 * 英文/数字按连续段转小写；中文按 bigram（单字段落保留单字），标点与空白作分隔。
 * 最终分 = 向量分 × (1 - weight) + 词项覆盖率 × weight；weight 收敛到 [0,1]。
 * 查询没有可用词项时保持原序，由调用方标记“未重排”。</p>
 */
@Component
public class RagLexicalReranker {

    private static final int BIGRAM_LENGTH = 2;
    private static final double MIN_WEIGHT = 0.0d;
    private static final double MAX_WEIGHT = 1.0d;
    private static final double ONE = 1.0d;

    /**
     * 提取查询词项（去重保序）。
     *
     * @param query 查询文本
     * @return 词项集合；空查询返回空集合
     */
    public Set<String> terms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        if (Objects.isNull(query) || query.isEmpty()) {
            return terms;
        }
        int index = 0;
        int length = query.length();
        while (index < length) {
            int codePoint = query.codePointAt(index);
            if (isHan(codePoint)) {
                int end = index;
                while (end < length && isHan(query.codePointAt(end))) {
                    end += Character.charCount(query.codePointAt(end));
                }
                addHanTerms(query.substring(index, end), terms);
                index = end;
            } else if (Character.isLetterOrDigit(codePoint)) {
                int end = index;
                while (end < length && Character.isLetterOrDigit(query.codePointAt(end))) {
                    end += Character.charCount(query.codePointAt(end));
                }
                terms.add(query.substring(index, end).toLowerCase(Locale.ROOT));
                index = end;
            } else {
                index += Character.charCount(codePoint);
            }
        }
        return terms;
    }

    /**
     * 按向量分与词项覆盖率重排。
     *
     * @param query      查询文本
     * @param candidates 候选切片（按向量分降序）
     * @param weight     词项覆盖率权重；超出 [0,1] 自动收敛
     * @return 重排后的列表；无词项或无候选时原样返回
     */
    public List<RagRetrievedChunk> rerank(String query, List<RagRetrievedChunk> candidates,
                                          double weight) {
        Set<String> terms = terms(query);
        if (terms.isEmpty() || Objects.isNull(candidates) || candidates.isEmpty()) {
            return Objects.isNull(candidates) ? List.of() : candidates;
        }
        double safeWeight = Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
        List<RagRetrievedChunk> scored = new ArrayList<>(candidates.size());
        for (RagRetrievedChunk candidate : candidates) {
            double coverage = coverage(terms, candidate.content());
            double finalScore = candidate.score() * (ONE - safeWeight) + coverage * safeWeight;
            scored.add(new RagRetrievedChunk(candidate.chunkId(), candidate.docId(),
                    candidate.projectId(), candidate.source(), candidate.chunkIndex(),
                    candidate.content(), candidate.score(), finalScore));
        }
        // List.sort 是稳定排序：同分时保留向量分顺序。
        scored.sort(Comparator.comparingDouble(RagRetrievedChunk::rerankScore).reversed());
        return scored;
    }

    /**
     * 计算词项覆盖率（供字面召回打分复用）。
     *
     * @param terms   词项集合
     * @param content 切片正文
     * @return 命中词项占比（0~1）
     */
    public double coverage(Set<String> terms, String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        long hit = terms.stream().filter(normalized::contains).count();
        return (double) hit / (double) terms.size();
    }

    private void addHanTerms(String segment, Set<String> terms) {
        int[] codePoints = segment.codePoints().toArray();
        if (codePoints.length == 1) {
            terms.add(segment);
            return;
        }
        for (int index = 0; index + BIGRAM_LENGTH <= codePoints.length; index++) {
            terms.add(new String(codePoints, index, BIGRAM_LENGTH));
        }
    }

    private boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }
}
