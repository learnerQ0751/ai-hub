package com.qtua.aihub.integration.rag.parse;

import com.qtua.aihub.common.config.RagProperties;

import java.util.Locale;
import java.util.Objects;

/**
 * 文档解析限制（REQ-3.9）。
 *
 * <p>由 {@code gateway.rag.parse.*} 构造，随文档类型复用；所有上限均为正数，
 * 配置缺失或非正数时回退到默认值。超限策略：{@link LimitPolicy#FAIL} 抛异常，
 * {@link LimitPolicy#TRUNCATE} 截断并追加标记。</p>
 *
 * @param maxChars             解析输出最大字符数
 * @param maxRowsPerSheet      单个工作表最大行数
 * @param maxCellsPerSheet     单个工作表最大单元格数
 * @param maxSheets            最大工作表数
 * @param maxColumns           最大列数
 * @param maxTableRows         Word 表格最大行数
 * @param maxTableCells        Word 表格最大单元格数
 * @param maxSlides            最大幻灯片数
 * @param maxShapesPerSlide    每页最大形状数
 * @param maxZipEntries        最大 ZIP 条目数
 * @param maxZipEntryBytes     单个 ZIP 条目解压上限（字节）
 * @param maxZipTotalBytes     ZIP 解压总大小上限（字节）
 * @param includeHeadersFooters 是否提取 Word 页眉页脚
 * @param includeNotes         是否提取 PPT 备注
 * @param limitPolicy          超限策略
 */
public record ParseLimits(
        int maxChars,
        int maxRowsPerSheet,
        int maxCellsPerSheet,
        int maxSheets,
        int maxColumns,
        int maxTableRows,
        int maxTableCells,
        int maxSlides,
        int maxShapesPerSlide,
        int maxZipEntries,
        long maxZipEntryBytes,
        long maxZipTotalBytes,
        boolean includeHeadersFooters,
        boolean includeNotes,
        LimitPolicy limitPolicy) {

    /** 默认输出字符上限。 */
    public static final int DEFAULT_MAX_CHARS = 5_000_000;
    /** 默认单表行数上限。 */
    public static final int DEFAULT_MAX_ROWS_PER_SHEET = 50_000;
    /** 默认单表单元格上限。 */
    public static final int DEFAULT_MAX_CELLS_PER_SHEET = 500_000;
    /** 默认工作表数上限。 */
    public static final int DEFAULT_MAX_SHEETS = 200;
    /** 默认列数上限。 */
    public static final int DEFAULT_MAX_COLUMNS = 256;
    /** 默认 Word 表格行数上限。 */
    public static final int DEFAULT_MAX_TABLE_ROWS = 10_000;
    /** 默认 Word 表格单元格上限。 */
    public static final int DEFAULT_MAX_TABLE_CELLS = 100_000;
    /** 默认幻灯片数上限。 */
    public static final int DEFAULT_MAX_SLIDES = 2_000;
    /** 默认每页形状数上限。 */
    public static final int DEFAULT_MAX_SHAPES_PER_SLIDE = 5_000;
    /** 默认 ZIP 条目数上限。 */
    public static final int DEFAULT_MAX_ZIP_ENTRIES = 10_000;
    /** 默认单条目解压上限：64MB。 */
    public static final long DEFAULT_MAX_ZIP_ENTRY_BYTES = 64L * 1024 * 1024;
    /** 默认解压总大小上限：256MB。 */
    public static final long DEFAULT_MAX_ZIP_TOTAL_BYTES = 256L * 1024 * 1024;

    /**
     * 超限策略。
     */
    public enum LimitPolicy {
        /** 抛异常（默认，避免隐性内容不完整）。 */
        FAIL,
        /** 截断并追加标记。 */
        TRUNCATE
    }

    /**
     * 从配置构造限制；非正数回退默认值。
     *
     * @param settings 解析配置
     * @return 解析限制
     */
    public static ParseLimits from(RagProperties.Parse settings) {
        return new ParseLimits(
                positive(settings.getMaxChars(), DEFAULT_MAX_CHARS),
                positive(settings.getMaxRowsPerSheet(), DEFAULT_MAX_ROWS_PER_SHEET),
                positive(settings.getMaxCellsPerSheet(), DEFAULT_MAX_CELLS_PER_SHEET),
                positive(settings.getMaxSheets(), DEFAULT_MAX_SHEETS),
                positive(settings.getMaxColumns(), DEFAULT_MAX_COLUMNS),
                positive(settings.getMaxTableRows(), DEFAULT_MAX_TABLE_ROWS),
                positive(settings.getMaxTableCells(), DEFAULT_MAX_TABLE_CELLS),
                positive(settings.getMaxSlides(), DEFAULT_MAX_SLIDES),
                positive(settings.getMaxShapesPerSlide(), DEFAULT_MAX_SHAPES_PER_SLIDE),
                positive(settings.getMaxZipEntries(), DEFAULT_MAX_ZIP_ENTRIES),
                positive(settings.getMaxZipEntryBytes(), DEFAULT_MAX_ZIP_ENTRY_BYTES),
                positive(settings.getMaxZipTotalBytes(), DEFAULT_MAX_ZIP_TOTAL_BYTES),
                settings.isIncludeHeadersFooters(),
                settings.isIncludeNotes(),
                policyOf(settings.getLimitPolicy()));
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static long positive(long value, long fallback) {
        return value > 0L ? value : fallback;
    }

    private static LimitPolicy policyOf(String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            return LimitPolicy.FAIL;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("truncate".equals(normalized)) {
            return LimitPolicy.TRUNCATE;
        }
        if ("fail".equals(normalized)) {
            return LimitPolicy.FAIL;
        }
        throw new IllegalArgumentException(
                "gateway.rag.parse.limit-policy 必须为 fail 或 truncate，当前值：" + value);
    }
}
