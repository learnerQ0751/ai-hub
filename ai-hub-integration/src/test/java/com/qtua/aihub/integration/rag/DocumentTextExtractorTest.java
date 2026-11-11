package com.qtua.aihub.integration.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-3.1 文本提取测试：txt/md 严格 UTF-8、PDFBox、损坏文件与不支持类型。
 */
class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extracts_txt_and_normalizes_line_endings() {
        String text = extractor.extract("demo.txt", "第一行\r\n\r\n第二行".getBytes(StandardCharsets.UTF_8));

        assertThat(text).isEqualTo("第一行\n\n第二行");
    }

    @Test
    void rejects_invalid_utf8() {
        byte[] invalid = new byte[]{(byte) 0xC3, (byte) 0x28};

        assertThatThrownBy(() -> extractor.extract("demo.md", invalid))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void rejects_unsupported_extension() {
        assertThatThrownBy(() -> extractor.extract("demo.docx", "x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("不支持");
    }

    @Test
    void rejects_corrupt_pdf() {
        assertThatThrownBy(() -> extractor.extract("broken.pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void extracts_pdf_text() throws IOException {
        byte[] pdf = pdfWithText("hello rag pdf");

        String text = extractor.extract("demo.pdf", pdf);

        assertThat(text).contains("hello rag pdf");
    }

    @Test
    void rejects_empty_document() {
        assertThatThrownBy(() -> extractor.extract("empty.txt", "   ".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("为空");
    }

    private byte[] pdfWithText(String content) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 700);
                stream.showText(content);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
