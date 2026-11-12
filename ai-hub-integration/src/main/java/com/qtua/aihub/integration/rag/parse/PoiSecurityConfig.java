package com.qtua.aihub.integration.rag.parse;

import jakarta.annotation.PostConstruct;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.util.IOUtils;
import org.springframework.stereotype.Component;

/**
 * POI 全局安全限制（REQ-3.9）。
 *
 * <p>OOXML 是 ZIP，必须在任何解析发生前限制解压比、单条目大小与文本节点大小，防止
 * 解压炸弹打满内存。这些是 JVM 全局静态设置，启动时应用一次；解析器构造时也会幂等
 * 应用，保证不经 Spring 的单元测试同样受保护。</p>
 */
@Component
public class PoiSecurityConfig {

    /** 最低压缩比：低于 1% 视为可疑压缩（POI 默认值，显式固定）。 */
    private static final double MIN_INFLATE_RATIO = 0.01d;
    /** 单个 ZIP 条目解压上限：64MB。 */
    private static final long MAX_ENTRY_SIZE = 64L * 1024 * 1024;
    /** 文本节点上限：10M 字符。 */
    private static final long MAX_TEXT_SIZE = 10_000_000L;
    /** POI 内部字节数组上限：256MB。 */
    private static final int MAX_BYTE_ARRAY = 256 * 1024 * 1024;

    /**
     * 容器启动时应用全局限制。
     */
    @PostConstruct
    public void init() {
        applyDefaults();
    }

    /**
     * 应用 POI 全局限制（幂等）。
     */
    public static void applyDefaults() {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_SIZE);
        ZipSecureFile.setMaxTextSize(MAX_TEXT_SIZE);
        IOUtils.setByteArrayMaxOverride(MAX_BYTE_ARRAY);
    }
}
