package com.qtua.aihub.integration.rag.parse;

import com.qtua.aihub.integration.rag.DocumentParseException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 文档魔数与 OOXML 子类型检测（REQ-3.9）。
 *
 * <p>扩展名决定解析器路由，魔数做二次校验，防止「改后缀」绕过解析器。OOXML 预检
 * 通过临时文件 + {@link ZipFile} 读取 ZIP 中央目录，只统计条目数与声明解压大小，
 * 不解压内容；声明大小可被伪造，实际解压仍由 POI {@code ZipSecureFile} 兜底。</p>
 */
public final class DocumentFormatDetector {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};
    private static final byte[] ZIP_EMPTY_MAGIC = {'P', 'K', 0x05, 0x06};
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final String ENCRYPTION_INFO = "EncryptionInfo";
    private static final String ENCRYPTED_PACKAGE = "EncryptedPackage";
    private static final String TEMP_PREFIX = "aihub-ooxml-";
    private static final String TEMP_SUFFIX = ".zip";
    private static final String ENTRY_DOCX = "word/document.xml";
    private static final String ENTRY_PPTX = "ppt/presentation.xml";
    private static final String ENTRY_XLSX = "xl/workbook.xml";

    private DocumentFormatDetector() {
    }

    /**
     * OOXML 子类型。
     */
    public enum OoxmlKind {
        /** Word 文档。 */
        DOCX,
        /** PowerPoint 演示文稿。 */
        PPTX,
        /** Excel 工作簿。 */
        XLSX,
        /** ZIP 但无法识别 OOXML 入口。 */
        UNKNOWN,
        /** 不是 ZIP。 */
        NOT_ZIP
    }

    /**
     * 是否 PDF 魔数。
     *
     * @param content 文件字节
     * @return true 表示以 %PDF- 开头
     */
    public static boolean isPdf(byte[] content) {
        return startsWith(content, PDF_MAGIC);
    }

    /**
     * 是否 ZIP 魔数。
     *
     * @param content 文件字节
     * @return true 表示 ZIP
     */
    public static boolean isZip(byte[] content) {
        return startsWith(content, ZIP_MAGIC) || startsWith(content, ZIP_EMPTY_MAGIC);
    }

    /**
     * 是否 OLE2 复合文档（老 Office / 加密 OOXML）。
     *
     * @param content 文件字节
     * @return true 表示 OLE2
     */
    public static boolean isOle2(byte[] content) {
        return startsWith(content, OLE2_MAGIC);
    }

    /**
     * 是否加密 OOXML（OLE2 容器内含加密流）。
     *
     * @param content 文件字节
     * @return true 表示加密 OOXML
     */
    public static boolean isEncryptedOoxml(byte[] content) {
        if (!isOle2(content)) {
            return false;
        }
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(content))) {
            return fileSystem.getRoot().hasEntry(ENCRYPTION_INFO)
                    || fileSystem.getRoot().hasEntry(ENCRYPTED_PACKAGE);
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * 检测 OOXML 子类型并校验 ZIP 条目数与声明解压大小。
     *
     * @param content 文件字节
     * @param limits  解析限制
     * @return OOXML 子类型
     * @throws DocumentParseException ZIP 损坏或解压限制超限
     */
    public static OoxmlKind detectOoxml(byte[] content, ParseLimits limits) {
        if (!isZip(content)) {
            return OoxmlKind.NOT_ZIP;
        }
        Path temp = null;
        try {
            temp = Files.createTempFile(TEMP_PREFIX, TEMP_SUFFIX);
            Files.write(temp, content);
            return inspectZip(temp, limits);
        } catch (IOException ex) {
            throw new DocumentParseException("文件损坏，无法解压", ex);
        } finally {
            deleteQuietly(temp);
        }
    }

    private static OoxmlKind inspectZip(Path path, ParseLimits limits) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            long total = 0L;
            OoxmlKind kind = OoxmlKind.UNKNOWN;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                count++;
                if (count > limits.maxZipEntries()) {
                    throw exceed("条目数", limits.maxZipEntries());
                }
                if (!entry.isDirectory()) {
                    long size = entry.getSize();
                    if (size > limits.maxZipEntryBytes()) {
                        throw exceed("单条目大小", limits.maxZipEntryBytes());
                    }
                    if (size > 0L) {
                        total += size;
                    }
                    if (total > limits.maxZipTotalBytes()) {
                        throw exceed("解压总大小", limits.maxZipTotalBytes());
                    }
                }
                if (kind == OoxmlKind.UNKNOWN) {
                    kind = kindOf(entry.getName(), kind);
                }
            }
            return kind;
        }
    }

    private static OoxmlKind kindOf(String entryName, OoxmlKind current) {
        if (ENTRY_DOCX.equals(entryName)) {
            return OoxmlKind.DOCX;
        }
        if (ENTRY_PPTX.equals(entryName)) {
            return OoxmlKind.PPTX;
        }
        if (ENTRY_XLSX.equals(entryName)) {
            return OoxmlKind.XLSX;
        }
        return current;
    }

    private static DocumentParseException exceed(String item, long limit) {
        return new DocumentParseException("文件解压后" + item + "超过上限（" + limit + "）", null);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static void deleteQuietly(Path path) {
        if (Objects.isNull(path)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件清理失败不影响解析结果，交给系统临时目录回收。
        }
    }
}
