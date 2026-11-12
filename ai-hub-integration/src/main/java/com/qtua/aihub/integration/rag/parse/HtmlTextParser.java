package com.qtua.aihub.integration.rag.parse;

import com.qtua.aihub.integration.rag.DocumentParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HTML（.html / .htm）文本解析器（REQ-3.9）。
 *
 * <p>jsoup 解析并自动探测编码（BOM / {@code meta charset}）；移除脚本、样式与嵌入对象后，
 * 优先取 {@code article}/{@code main} 正文，否则取 {@code body}。渲染时保留块级换行、
 * 列表符号与表格制表符，嵌套深度有上限，避免恶意深嵌套导致栈溢出。</p>
 */
@Component
public class HtmlTextParser implements DocumentTextParser {

    private static final Set<String> EXTENSIONS = Set.of("html", "htm");
    private static final int MAX_DEPTH = 256;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String REMOVED_SELECTOR =
            "script, style, noscript, iframe, svg, canvas, template, object, embed";
    private static final String BODY_SELECTOR = "article, main";
    private static final Set<String> BLOCK_TAGS = Set.of("p", "div", "section", "article",
            "header", "footer", "nav", "main", "aside", "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "tr", "table", "blockquote", "figure", "figcaption", "form",
            "address", "hr");
    private static final String MISMATCH_MESSAGE = "文件内容与扩展名不一致（期望 html）";
    private static final String CORRUPT_MESSAGE = "HTML 文件解析失败，请确认文件未损坏";
    private static final String TOO_DEEP_MESSAGE = "HTML 结构嵌套过深，无法解析";

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String formatName() {
        return "html";
    }

    @Override
    public String parse(byte[] content, ParseLimits limits) {
        rejectBinary(content);
        try {
            Document document = Jsoup.parse(new ByteArrayInputStream(content), null, "");
            document.select(REMOVED_SELECTOR).remove();
            Element root = document.selectFirst(BODY_SELECTOR);
            if (Objects.isNull(root)) {
                root = document.body();
            }
            if (Objects.isNull(root)) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            render(root, out, 0);
            return out.toString();
        } catch (IOException ex) {
            throw new DocumentParseException(CORRUPT_MESSAGE, ex);
        }
    }

    private void rejectBinary(byte[] content) {
        if (DocumentFormatDetector.isPdf(content) || DocumentFormatDetector.isZip(content)
                || DocumentFormatDetector.isOle2(content)) {
            throw new DocumentParseException(MISMATCH_MESSAGE, null);
        }
    }

    private void render(Node node, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new DocumentParseException(TOO_DEEP_MESSAGE, null);
        }
        if (node instanceof TextNode textNode) {
            String normalized = textNode.text().replace(' ', ' ');
            out.append(WHITESPACE.matcher(normalized).replaceAll(" "));
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }
        String tag = element.tagName();
        if ("br".equals(tag)) {
            out.append('\n');
            return;
        }
        if ("pre".equals(tag)) {
            out.append(element.wholeText()).append('\n');
            return;
        }
        if ("li".equals(tag)) {
            out.append("- ");
        }
        for (Node child : element.childNodes()) {
            render(child, out, depth + 1);
        }
        if ("td".equals(tag) || "th".equals(tag)) {
            out.append('\t');
        }
        if (BLOCK_TAGS.contains(tag)) {
            out.append('\n');
        }
    }
}
