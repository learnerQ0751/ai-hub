package com.qtua.aihub.integration.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.integration.rag.parse.DocxTextParser;
import com.qtua.aihub.integration.rag.parse.HtmlTextParser;
import com.qtua.aihub.integration.rag.parse.ParseLimits;
import com.qtua.aihub.integration.rag.parse.PdfTextParser;
import com.qtua.aihub.integration.rag.parse.PlainTextParser;
import com.qtua.aihub.integration.rag.parse.PptxTextParser;
import com.qtua.aihub.integration.rag.parse.XlsxTextParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-3.9 文本提取测试：注册表、txt/md/pdf 回归、docx/pptx/xlsx/html 与老 .doc 拒绝。
 */
class DocumentTextExtractorTest {

    private final RagProperties properties = new RagProperties();

    private final DocumentTextExtractor extractor = new DocumentTextExtractor(List.of(
            new PlainTextParser(), new PdfTextParser(), new DocxTextParser(),
            new PptxTextParser(), new XlsxTextParser(), new HtmlTextParser()), properties);

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
        assertThatThrownBy(() -> extractor.extract("demo.xyz", "x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("不支持");
    }

    @Test
    void rejects_legacy_doc_with_actionable_message() {
        assertThatThrownBy(() -> extractor.extract("legacy.doc", new byte[]{1, 2, 3}))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("另存为 .docx");
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

    @Test
    void supports_new_formats() {
        assertThat(extractor.supports("a.docx")).isTrue();
        assertThat(extractor.supports("a.pptx")).isTrue();
        assertThat(extractor.supports("a.xlsx")).isTrue();
        assertThat(extractor.supports("a.html")).isTrue();
        assertThat(extractor.supports("a.htm")).isTrue();
        assertThat(extractor.supports("a.doc")).isFalse();
        assertThat(extractor.supports("a.txt")).isTrue();
    }

    @Test
    void extracts_docx_paragraphs_and_tables_in_document_order() throws IOException {
        byte[] docx = docxWithParagraphAndTable();

        String text = extractor.extract("demo.docx", docx);

        assertThat(text).contains("第一段").contains("标题A").contains("值A").contains("第二段");
        assertThat(text.indexOf("第一段")).isLessThan(text.indexOf("标题A"));
        assertThat(text.indexOf("标题A")).isLessThan(text.indexOf("第二段"));
    }

    @Test
    void extracts_pptx_slides_with_separator() throws IOException {
        byte[] pptx = pptxWithTwoSlides();

        String text = extractor.extract("demo.pptx", pptx);

        assertThat(text).contains("第 1 页").contains("第一页内容");
        assertThat(text).contains("第 2 页").contains("第二页内容");
        assertThat(text.indexOf("第一页内容")).isLessThan(text.indexOf("第二页内容"));
    }

    @Test
    void extracts_xlsx_sheets_as_tsv() throws IOException {
        byte[] xlsx = xlsxWithPriceSheet();

        String text = extractor.extract("demo.xlsx", xlsx);

        assertThat(text).contains("工作表: 产品价格");
        assertThat(text).contains("产品\t单价");
        assertThat(text).contains("A\t100");
    }

    @Test
    void extracts_html_body_without_script_or_style() {
        String html = "<html><head><style>.x{color:red}</style><script>alert('x')</script>"
                + "</head><body><nav>导航</nav><article><h1>标题</h1><p>正文内容</p>"
                + "<ul><li>要点一</li><li>要点二</li></ul></article></body></html>";

        String text = extractor.extract("demo.html", html.getBytes(StandardCharsets.UTF_8));

        assertThat(text).contains("标题").contains("正文内容").contains("- 要点一");
        assertThat(text).doesNotContain("alert").doesNotContain("color:red");
    }

    @Test
    void rejects_zip_that_is_not_matching_extension() throws IOException {
        byte[] zip = plainZip("readme.txt", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> extractor.extract("fake.docx", zip))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void fails_when_text_exceeds_max_chars() {
        properties.getParse().setMaxChars(10);
        properties.getParse().setLimitPolicy("fail");
        DocumentTextExtractor limited = new DocumentTextExtractor(List.of(
                new PlainTextParser(), new PdfTextParser(), new DocxTextParser(),
                new PptxTextParser(), new XlsxTextParser(), new HtmlTextParser()), properties);

        assertThatThrownBy(() -> limited.extract("long.txt",
                "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void truncates_when_policy_is_truncate() {
        properties.getParse().setMaxChars(10);
        properties.getParse().setLimitPolicy("truncate");
        DocumentTextExtractor limited = new DocumentTextExtractor(List.of(
                new PlainTextParser(), new PdfTextParser(), new DocxTextParser(),
                new PptxTextParser(), new XlsxTextParser(), new HtmlTextParser()), properties);

        String text = limited.extract("long.txt",
                "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8));

        assertThat(text).hasSizeLessThan(60);
        assertThat(text).contains("内容已截断");
    }

    @Test
    void exposes_default_parse_limits() {
        ParseLimits limits = new ParseLimits(5_000_000, 50_000, 500_000, 200, 256, 10_000,
                100_000, 2_000, 5_000, 10_000, 67_108_864L, 268_435_456L, true, false,
                ParseLimits.LimitPolicy.FAIL);

        assertThat(limits.maxChars()).isEqualTo(5_000_000);
        assertThat(limits.limitPolicy()).isEqualTo(ParseLimits.LimitPolicy.FAIL);
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

    private byte[] docxWithParagraphAndTable() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("第一段");
            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("标题A");
            table.getRow(0).getCell(1).setText("值A");
            document.createParagraph().createRun().setText("第二段");
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] pptxWithTwoSlides() throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSLFSlide first = slideShow.createSlide();
            XSLFTextBox firstBox = first.createTextBox();
            firstBox.setText("第一页内容");
            XSLFSlide second = slideShow.createSlide();
            XSLFTextBox secondBox = second.createTextBox();
            secondBox.setText("第二页内容");
            slideShow.write(output);
            return output.toByteArray();
        }
    }

    private byte[] xlsxWithPriceSheet() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("产品价格");
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("产品");
            header.createCell(1).setCellValue("单价");
            XSSFRow data = sheet.createRow(1);
            data.createCell(0).setCellValue("A");
            data.createCell(1).setCellValue(100);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] plainZip(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(entryName));
            zip.write(content);
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
