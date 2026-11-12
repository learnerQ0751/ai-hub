package com.qtua.aihub.integration.rag.parse;

import com.qtua.aihub.integration.rag.DocumentParseException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * PDF 文本解析器。
 *
 * <p>使用 PDFBox 提取文本层；扫描件/无文本 PDF 返回空文本，由门面统一判空。
 * 加密 PDF 给出中文提示。</p>
 */
@Component
public class PdfTextParser implements DocumentTextParser {

    private static final Set<String> EXTENSIONS = Set.of("pdf");

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String formatName() {
        return "pdf";
    }

    @Override
    public String parse(byte[] content, ParseLimits limits) {
        if (!DocumentFormatDetector.isPdf(content)) {
            throw new DocumentParseException("文件内容与扩展名不一致（期望 PDF）", null);
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        } catch (InvalidPasswordException ex) {
            throw new DocumentParseException("不支持加密 PDF，请先解除密码后再上传", ex);
        } catch (IOException ex) {
            throw new DocumentParseException("PDF 文件解析失败，请确认文件未损坏", ex);
        }
    }
}
