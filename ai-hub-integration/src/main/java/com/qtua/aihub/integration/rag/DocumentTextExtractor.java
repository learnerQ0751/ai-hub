package com.qtua.aihub.integration.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 文档文本提取（txt / md / pdf）。
 *
 * <p>PDF 使用 PDFBox 解析文本；扫描件/无文本 PDF 返回解析失败。文本严格按 UTF-8
 * 解码，非法字节直接报错而不是静默替换。外部库异常统一转换为
 * {@link DocumentParseException}，不向调用方泄露堆栈。</p>
 */
@Component
public class DocumentTextExtractor {

    private static final String EXT_TXT = "txt";
    private static final String EXT_MD = "md";
    private static final String EXT_PDF = "pdf";
    private static final String FIELD_FILE = "file";

    /**
     * 是否支持该扩展名。
     *
     * @param fileName 文件名
     * @return true 表示支持
     */
    public boolean supports(String fileName) {
        String extension = extensionOf(fileName);
        return EXT_TXT.equals(extension) || EXT_MD.equals(extension) || EXT_PDF.equals(extension);
    }

    /**
     * 提取文本。
     *
     * @param fileName 文件名（用于判断格式）
     * @param content  文件字节
     * @return 归一化后的文本（非空）
     */
    public String extract(String fileName, byte[] content) {
        String extension = extensionOf(fileName);
        String text;
        if (EXT_PDF.equals(extension)) {
            text = extractPdf(content);
        } else if (EXT_TXT.equals(extension) || EXT_MD.equals(extension)) {
            text = decodeUtf8(content);
        } else {
            throw new DocumentParseException("不支持的文件类型，仅支持 txt / md / pdf", null);
        }
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            throw new DocumentParseException("文档内容为空，无法切分", null);
        }
        return normalized;
    }

    private String extractPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            throw new DocumentParseException("PDF 文件解析失败，请确认文件未损坏", ex);
        }
    }

    private String decodeUtf8(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer chars = decoder.decode(ByteBuffer.wrap(content));
            return chars.toString();
        } catch (CharacterCodingException ex) {
            throw new DocumentParseException("文本文件不是合法的 UTF-8 编码", ex);
        }
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String extensionOf(String fileName) {
        if (fileName.lastIndexOf('.') < 0) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 错误字段名（供 Service 构造统一错误体）。
     *
     * @return 字段名
     */
    public String fileParam() {
        return FIELD_FILE;
    }
}
