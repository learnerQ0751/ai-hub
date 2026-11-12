package com.qtua.aihub.common.metering;

import org.springframework.util.ObjectUtils;

/**
 * embedding 输入 token 估算。
 *
 * <p>供应商返回 usage 时以供应商为准；缺失时用本规则兜底：CJK 字符（中日韩）按 1 字≈1 token，
 * 其他字符按 4 字符≈1 token 向上取整。该规则对中文语料保守（不会低估），规则同时写入 REQ §5。</p>
 */
public final class EmbeddingTokenEstimator {

    /** 非 CJK 字符每 token 的字符数。 */
    private static final int CHARS_PER_TOKEN = 4;

    private EmbeddingTokenEstimator() {
    }

    /**
     * 估算单条文本 token 数。
     *
     * @param text 文本
     * @return token 估算（空文本为 0）
     */
    public static long estimate(CharSequence text) {
        if (ObjectUtils.isEmpty(text)) {
            return 0L;
        }
        long cjk = 0L;
        long other = 0L;
        int index = 0;
        while (index < text.length()) {
            int codePoint = Character.codePointAt(text, index);
            if (isCjk(codePoint)) {
                cjk++;
            } else {
                other++;
            }
            index += Character.charCount(codePoint);
        }
        return cjk + (other + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    /**
     * 估算多条文本 token 总数。
     *
     * @param texts 文本列表
     * @return token 估算
     */
    public static long estimateAll(Iterable<String> texts) {
        long total = 0L;
        for (String text : texts) {
            total += estimate(text);
        }
        return total;
    }

    /**
     * 按中文字符数估算 token（chunk 窗口校验使用，1 字≈1 token）。
     *
     * @param chars 字符数
     * @return token 估算
     */
    public static long estimateChineseChars(int chars) {
        return Math.max(0, chars);
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
