package com.qtua.aihub.integration.rag.parse;

import com.qtua.aihub.integration.rag.DocumentParseException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 纯文本解析器（txt / md）。
 *
 * <p>严格 UTF-8 解码，非法字节直接报错，避免静默替换掩盖编码问题。</p>
 */
@Component
public class PlainTextParser implements DocumentTextParser {

    private static final Set<String> EXTENSIONS = Set.of("txt", "md");

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String formatName() {
        return "text";
    }

    @Override
    public String parse(byte[] content, ParseLimits limits) {
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
}
