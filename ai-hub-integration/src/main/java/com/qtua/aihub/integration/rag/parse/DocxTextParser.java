package com.qtua.aihub.integration.rag.parse;

import com.qtua.aihub.integration.rag.DocumentParseException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Word（.docx）文本解析器（REQ-3.9）。
 *
 * <p>按 {@code getBodyElements()} 保持段落与表格的文档顺序；表格逐行逐单元格输出，
 * 单元格之间用制表符、行末换行，嵌套表格递归并限制深度。页眉页脚按 part 去重后
 * 追加（可配置关闭）。文本框、SmartArt、OMML 公式不在本期提取范围。</p>
 */
@Component
public class DocxTextParser implements DocumentTextParser {

    private static final Set<String> EXTENSIONS = Set.of("docx");
    private static final int MAX_TABLE_DEPTH = 10;
    private static final String ENCRYPTED_MESSAGE = "不支持加密文档，请先解除密码后再上传";
    private static final String NOT_DOCX_MESSAGE =
            "文件不是有效的 docx，请确认文件格式（老 .doc 请另存为 .docx）";
    private static final String MISMATCH_MESSAGE = "文件内容与扩展名不一致（期望 docx）";
    private static final String CORRUPT_MESSAGE = "文件格式损坏或与扩展名不符（docx）";

    /**
     * 构造解析器并应用 POI 全局安全限制。
     */
    public DocxTextParser() {
        PoiSecurityConfig.applyDefaults();
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String formatName() {
        return "docx";
    }

    @Override
    public String parse(byte[] content, ParseLimits limits) {
        rejectEncrypted(content);
        DocumentFormatDetector.OoxmlKind kind = DocumentFormatDetector.detectOoxml(content, limits);
        if (kind == DocumentFormatDetector.OoxmlKind.NOT_ZIP) {
            if (DocumentFormatDetector.isOle2(content)) {
                throw new DocumentParseException(NOT_DOCX_MESSAGE);
            }
            throw new DocumentParseException(MISMATCH_MESSAGE);
        }
        if (kind != DocumentFormatDetector.OoxmlKind.DOCX) {
            throw new DocumentParseException(MISMATCH_MESSAGE);
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            return extract(document, limits);
        } catch (EncryptedDocumentException ex) {
            throw new DocumentParseException(ENCRYPTED_MESSAGE, ex);
        } catch (OLE2NotOfficeXmlFileException ex) {
            throw new DocumentParseException(NOT_DOCX_MESSAGE, ex);
        } catch (DocumentParseException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new DocumentParseException(CORRUPT_MESSAGE, ex);
        }
    }

    private void rejectEncrypted(byte[] content) {
        if (DocumentFormatDetector.isEncryptedOoxml(content)) {
            throw new DocumentParseException(ENCRYPTED_MESSAGE);
        }
    }

    private String extract(XWPFDocument document, ParseLimits limits) {
        StringBuilder out = new StringBuilder();
        TableCounter counter = new TableCounter();
        appendBody(document.getBodyElements(), out, limits, counter);
        if (limits.includeHeadersFooters()) {
            appendHeadersFooters(document, out);
        }
        return out.toString();
    }

    private void appendBody(List<IBodyElement> elements, StringBuilder out, ParseLimits limits,
                            TableCounter counter) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph paragraph) {
                appendParagraph(paragraph, out);
            } else if (element instanceof XWPFTable table) {
                appendTable(table, out, limits, counter, 0);
            }
        }
    }

    private void appendParagraph(XWPFParagraph paragraph, StringBuilder out) {
        String text = paragraph.getText();
        if (StringUtils.hasText(text)) {
            out.append(text).append('\n');
        }
    }

    private void appendTable(XWPFTable table, StringBuilder out, ParseLimits limits,
                             TableCounter counter, int depth) {
        if (depth >= MAX_TABLE_DEPTH) {
            throw new DocumentParseException("文档表格嵌套过深，无法解析", null);
        }
        for (XWPFTableRow row : table.getRows()) {
            counter.rows++;
            if (counter.rows > limits.maxTableRows()) {
                throw new DocumentParseException(
                        "文档表格行数超过上限（" + limits.maxTableRows() + "）", null);
            }
            List<XWPFTableCell> cells = row.getTableCells();
            for (int index = 0; index < cells.size(); index++) {
                counter.cells++;
                if (counter.cells > limits.maxTableCells()) {
                    throw new DocumentParseException(
                            "文档表格单元格数超过上限（" + limits.maxTableCells() + "）", null);
                }
                if (index > 0) {
                    out.append('\t');
                }
                appendCell(cells.get(index), out, limits, counter, depth + 1);
            }
            out.append('\n');
        }
    }

    private void appendCell(XWPFTableCell cell, StringBuilder out, ParseLimits limits,
                            TableCounter counter, int depth) {
        boolean first = true;
        for (IBodyElement element : cell.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                String text = paragraph.getText();
                if (StringUtils.hasText(text)) {
                    if (!first) {
                        out.append(' ');
                    }
                    out.append(text.trim());
                    first = false;
                }
            } else if (element instanceof XWPFTable nested) {
                if (!first) {
                    out.append(' ');
                }
                appendTable(nested, out, limits, counter, depth);
                first = false;
            }
        }
    }

    private void appendHeadersFooters(XWPFDocument document, StringBuilder out) {
        Set<String> seen = new HashSet<>();
        for (XWPFHeader header : document.getHeaderList()) {
            if (seen.add(header.getPackagePart().getPartName().getName())) {
                appendBodyElements(header.getBodyElements(), out);
            }
        }
        for (XWPFFooter footer : document.getFooterList()) {
            if (seen.add(footer.getPackagePart().getPartName().getName())) {
                appendBodyElements(footer.getBodyElements(), out);
            }
        }
    }

    private void appendBodyElements(List<IBodyElement> elements, StringBuilder out) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph paragraph
                    && StringUtils.hasText(paragraph.getText())) {
                out.append(paragraph.getText()).append('\n');
            } else if (element instanceof XWPFTable table) {
                out.append(table.getText()).append('\n');
            }
        }
    }

    /**
     * 表格行/单元格累计计数（全文档范围）。
     */
    private static final class TableCounter {

        private int rows;
        private int cells;
    }
}
