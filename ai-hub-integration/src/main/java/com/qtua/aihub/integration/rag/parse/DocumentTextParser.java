package com.qtua.aihub.integration.rag.parse;

import com.qtua.aihub.integration.rag.DocumentParseException;

import java.util.Set;

/**
 * 单一格式的文本解析器（REQ-3.9）。
 *
 * <p>实现必须无状态、线程安全；只负责「字节 → 文本」，不处理 DTO、存储与切分。
 * 解析器由 Spring 注册，{@code DocumentTextExtractor} 按扩展名路由。</p>
 */
public interface DocumentTextParser {

    /**
     * 支持的扩展名（小写、不含点）。
     *
     * @return 扩展名集合
     */
    Set<String> supportedExtensions();

    /**
     * 格式名，用于日志与错误信息。
     *
     * @return 格式名
     */
    String formatName();

    /**
     * 提取文本。
     *
     * @param content 文件字节
     * @param limits  解析限制
     * @return 原始文本（允许空，由门面统一判空）
     * @throws DocumentParseException 格式损坏、加密、超限或内容与格式不符
     */
    String parse(byte[] content, ParseLimits limits);
}
