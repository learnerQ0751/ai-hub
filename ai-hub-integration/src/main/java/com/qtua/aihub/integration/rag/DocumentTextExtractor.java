package com.qtua.aihub.integration.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.integration.rag.parse.DocumentTextParser;
import com.qtua.aihub.integration.rag.parse.ParseLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文档文本提取门面（REQ-3.1 / REQ-3.9）。
 *
 * <p>按扩展名把文件路由到 {@link DocumentTextParser} 实现；对外保持
 * {@link #supports(String)} / {@link #extract(String, byte[])} / {@link #fileParam()} 不变。
 * 统一负责换行归一化、空文本判空与输出字符上限；老 .doc/.xls/.ppt 给出另存为指引。
 * 本类无可变业务状态，多实例安全。</p>
 */
@Component
public class DocumentTextExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentTextExtractor.class);

    private static final String FIELD_FILE = "file";
    private static final String TRUNCATE_MARKER = "\n\n[内容已截断：超出解析上限]";
    private static final Map<String, String> LEGACY_GUIDANCE = Map.of(
            "doc", "暂不支持 .doc 格式，请用 Word 另存为 .docx 后重新上传",
            "xls", "暂不支持 .xls 格式，请用 Excel 另存为 .xlsx 后重新上传",
            "ppt", "暂不支持 .ppt 格式，请用 PowerPoint 另存为 .pptx 后重新上传");

    private final Map<String, DocumentTextParser> parsersByExtension;
    private final ParseLimits limits;

    /**
     * 构造门面，按解析器声明的扩展名建立索引。
     *
     * @param parsers    全部解析器（Spring 注入）
     * @param properties RAG 配置
     */
    public DocumentTextExtractor(List<DocumentTextParser> parsers, RagProperties properties) {
        Map<String, DocumentTextParser> registered = new LinkedHashMap<>();
        for (DocumentTextParser parser : parsers) {
            for (String extension : parser.supportedExtensions()) {
                DocumentTextParser previous = registered.putIfAbsent(extension, parser);
                if (Objects.nonNull(previous)) {
                    throw new IllegalStateException("重复注册文档扩展名: " + extension);
                }
            }
        }
        this.parsersByExtension = Map.copyOf(registered);
        this.limits = ParseLimits.from(properties.getParse());
    }

    /**
     * 是否支持该扩展名。
     *
     * @param fileName 文件名
     * @return true 表示支持
     */
    public boolean supports(String fileName) {
        return parsersByExtension.containsKey(extensionOf(fileName));
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
        DocumentTextParser parser = parsersByExtension.get(extension);
        if (Objects.isNull(parser)) {
            throw unsupported(extension);
        }
        String text = parser.parse(content, limits);
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            throw new DocumentParseException("文档内容为空，无法切分", null);
        }
        return applyLimit(normalized, extension);
    }

    private DocumentParseException unsupported(String extension) {
        String guidance = LEGACY_GUIDANCE.get(extension);
        if (Objects.nonNull(guidance)) {
            return new DocumentParseException(guidance);
        }
        String supported = parsersByExtension.keySet().stream().sorted()
                .collect(Collectors.joining(" / "));
        return new DocumentParseException("不支持的文件类型，仅支持 " + supported, null);
    }

    private String applyLimit(String text, String extension) {
        if (text.length() <= limits.maxChars()) {
            return text;
        }
        if (limits.limitPolicy() == ParseLimits.LimitPolicy.TRUNCATE) {
            LOG.warn("document text truncated: extension={}, maxChars={}",
                    extension, limits.maxChars());
            return text.substring(0, limits.maxChars()) + TRUNCATE_MARKER;
        }
        throw new DocumentParseException(
                "文档解析文本超过上限（" + limits.maxChars() + " 字符），请拆分后重新上传", null);
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String extensionOf(String fileName) {
        if (Objects.isNull(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
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
