package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.rag.RagChunkDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 段落优先切分器。
 *
 * <p>先按空行拆段落，再贪心打包为不超过 max-chars 的切片；单个段落超长时按
 * max-chars 定长切分，相邻窗口重叠 overlap-chars。输出顺序稳定、chunkIndex 从 0 开始。
 * 有效 max-chars 由 {@link RagChunkSettings} 按 embedding 模型窗口推导与校验。</p>
 */
@Component
public class RagChunker {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String PARAGRAPH_SPLIT_REGEX = "\\n\\s*\\n";

    private final int maxChars;
    private final int overlapChars;

    /**
     * 构造切分器。
     *
     * @param settings 切片参数
     */
    public RagChunker(RagChunkSettings settings) {
        this.maxChars = settings.maxChars();
        this.overlapChars = settings.overlapChars();
    }

    /**
     * 切分文本。
     *
     * @param text 已解析文本
     * @return 切片草稿；空文本返回空列表
     */
    public List<RagChunkDraft> split(String text) {
        List<RagChunkDraft> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawParagraph : text.split(PARAGRAPH_SPLIT_REGEX)) {
            String paragraph = rawParagraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (paragraph.length() > maxChars) {
                flush(current, chunks);
                appendLongParagraph(paragraph, chunks);
            } else if (current.isEmpty()) {
                current.append(paragraph);
            } else if (current.length() + PARAGRAPH_SEPARATOR.length() + paragraph.length()
                    <= maxChars) {
                current.append(PARAGRAPH_SEPARATOR).append(paragraph);
            } else {
                flush(current, chunks);
                current.append(paragraph);
            }
        }
        flush(current, chunks);
        return chunks;
    }

    private void appendLongParagraph(String paragraph, List<RagChunkDraft> chunks) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + maxChars, paragraph.length());
            String piece = paragraph.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(new RagChunkDraft(chunks.size(), piece));
            }
            if (end == paragraph.length()) {
                break;
            }
            start = end - overlapChars;
        }
    }

    private void flush(StringBuilder current, List<RagChunkDraft> chunks) {
        if (current.isEmpty()) {
            return;
        }
        String content = current.toString().trim();
        if (!content.isEmpty()) {
            chunks.add(new RagChunkDraft(chunks.size(), content));
        }
        current.setLength(0);
    }
}
