package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.RagChunkDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-3.1 切分器测试：段落优先、超长定长 + overlap、空文本。
 */
class RagChunkerTest {

    @Test
    void packs_short_paragraphs_into_one_chunk() {
        RagChunker chunker = chunker(80, 10);

        List<RagChunkDraft> chunks = chunker.split("第一段内容\n\n第二段内容");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).contains("第一段内容").contains("第二段内容");
        assertThat(chunks.get(0).chunkIndex()).isZero();
        assertThat(chunks.get(0).charCount()).isEqualTo(chunks.get(0).content().length());
    }

    @Test
    void splits_paragraphs_when_pack_exceeds_max_chars() {
        RagChunker chunker = chunker(10, 2);

        List<RagChunkDraft> chunks = chunker.split("abcdef\n\nghijkl");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("abcdef");
        assertThat(chunks.get(1).content()).isEqualTo("ghijkl");
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
    }

    @Test
    void splits_long_paragraph_with_overlap() {
        RagChunker chunker = chunker(10, 3);

        List<RagChunkDraft> chunks = chunker.split("abcdefghijklmnopqrstuvwxyz");

        assertThat(chunks.size()).isGreaterThan(1);
        for (RagChunkDraft chunk : chunks) {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(10);
        }
        String first = chunks.get(0).content();
        String second = chunks.get(1).content();
        assertThat(first.substring(first.length() - 3)).isEqualTo(second.substring(0, 3));
    }

    @Test
    void blank_text_returns_empty_list() {
        RagChunker chunker = chunker(20, 5);

        assertThat(chunker.split("   \n\n  ")).isEmpty();
    }

    private RagChunker chunker(int maxChars, int overlap) {
        RagProperties properties = new RagProperties();
        properties.getChunk().setMaxChars(maxChars);
        properties.getChunk().setOverlapChars(overlap);
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.maxInputTokens()).thenReturn(100_000);
        return new RagChunker(new RagChunkSettings(properties, model));
    }
}
