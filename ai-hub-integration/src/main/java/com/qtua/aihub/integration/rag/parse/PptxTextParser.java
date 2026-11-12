package com.qtua.aihub.integration.rag.parse;

import com.qtua.aihub.integration.rag.DocumentParseException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * PowerPoint（.pptx）文本解析器（REQ-3.9）。
 *
 * <p>按页提取文本框、表格与组合形状内文本，页间用「## 第 N 页」分隔。备注默认不提取
 * （可配置）。母版/版式不读取，避免页脚、页码重复；图表、SmartArt、嵌入对象不在本期
 * 提取范围。</p>
 */
@Component
public class PptxTextParser implements DocumentTextParser {

    private static final Set<String> EXTENSIONS = Set.of("pptx");
    private static final int MAX_SHAPE_DEPTH = 10;
    private static final String ENCRYPTED_MESSAGE = "不支持加密文档，请先解除密码后再上传";
    private static final String NOT_PPTX_MESSAGE =
            "文件不是有效的 pptx，请确认文件格式（老 .ppt 请另存为 .pptx）";
    private static final String MISMATCH_MESSAGE = "文件内容与扩展名不一致（期望 pptx）";
    private static final String CORRUPT_MESSAGE = "文件格式损坏或与扩展名不符（pptx）";

    /**
     * 构造解析器并应用 POI 全局安全限制。
     */
    public PptxTextParser() {
        PoiSecurityConfig.applyDefaults();
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String formatName() {
        return "pptx";
    }

    @Override
    public String parse(byte[] content, ParseLimits limits) {
        if (DocumentFormatDetector.isEncryptedOoxml(content)) {
            throw new DocumentParseException(ENCRYPTED_MESSAGE);
        }
        DocumentFormatDetector.OoxmlKind kind = DocumentFormatDetector.detectOoxml(content, limits);
        if (kind == DocumentFormatDetector.OoxmlKind.NOT_ZIP) {
            if (DocumentFormatDetector.isOle2(content)) {
                throw new DocumentParseException(NOT_PPTX_MESSAGE);
            }
            throw new DocumentParseException(MISMATCH_MESSAGE);
        }
        if (kind != DocumentFormatDetector.OoxmlKind.PPTX) {
            throw new DocumentParseException(MISMATCH_MESSAGE);
        }
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(content))) {
            return extract(slideShow, limits);
        } catch (EncryptedDocumentException ex) {
            throw new DocumentParseException(ENCRYPTED_MESSAGE, ex);
        } catch (OLE2NotOfficeXmlFileException ex) {
            throw new DocumentParseException(NOT_PPTX_MESSAGE, ex);
        } catch (DocumentParseException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new DocumentParseException(CORRUPT_MESSAGE, ex);
        }
    }

    private String extract(XMLSlideShow slideShow, ParseLimits limits) {
        List<XSLFSlide> slides = slideShow.getSlides();
        if (slides.size() > limits.maxSlides()) {
            throw new DocumentParseException(
                    "幻灯片数超过上限（" + limits.maxSlides() + "）", null);
        }
        StringBuilder out = new StringBuilder();
        int number = 1;
        for (XSLFSlide slide : slides) {
            out.append("## 第 ").append(number++).append(" 页\n");
            int shapes = 0;
            for (XSLFShape shape : slide.getShapes()) {
                shapes++;
                if (shapes > limits.maxShapesPerSlide()) {
                    throw new DocumentParseException(
                            "单页形状数超过上限（" + limits.maxShapesPerSlide() + "）", null);
                }
                appendShape(shape, out, 0);
            }
            if (limits.includeNotes() && Objects.nonNull(slide.getNotes())) {
                appendNotes(slide.getNotes(), out);
            }
        }
        return out.toString();
    }

    private void appendShape(XSLFShape shape, StringBuilder out, int depth) {
        if (depth > MAX_SHAPE_DEPTH) {
            throw new DocumentParseException("幻灯片组合形状嵌套过深，无法解析", null);
        }
        if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) {
                appendShape(child, out, depth + 1);
            }
        } else if (shape instanceof XSLFTable table) {
            appendTable(table, out);
        } else if (shape instanceof XSLFTextShape textShape) {
            String text = textShape.getText();
            if (StringUtils.hasText(text)) {
                out.append(text).append('\n');
            }
        }
    }

    private void appendTable(XSLFTable table, StringBuilder out) {
        for (XSLFTableRow row : table.getRows()) {
            List<XSLFTableCell> cells = row.getCells();
            for (int index = 0; index < cells.size(); index++) {
                if (index > 0) {
                    out.append('\t');
                }
                String text = cells.get(index).getText();
                if (StringUtils.hasText(text)) {
                    out.append(text.replace('\n', ' '));
                }
            }
            out.append('\n');
        }
    }

    private void appendNotes(XSLFNotes notes, StringBuilder out) {
        StringBuilder notesText = new StringBuilder();
        for (XSLFShape shape : notes.getShapes()) {
            if (shape instanceof XSLFTextShape textShape
                    && StringUtils.hasText(textShape.getText())) {
                notesText.append(textShape.getText()).append('\n');
            }
        }
        if (notesText.length() > 0) {
            out.append("### 备注\n").append(notesText);
        }
    }
}
