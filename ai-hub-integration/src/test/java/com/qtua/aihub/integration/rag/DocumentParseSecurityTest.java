package com.qtua.aihub.integration.rag;

import com.qtua.aihub.integration.rag.parse.DocxTextParser;
import com.qtua.aihub.integration.rag.parse.ParseLimits;
import com.qtua.aihub.integration.rag.parse.PptxTextParser;
import com.qtua.aihub.integration.rag.parse.XlsxTextParser;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-3.9 解析安全测试：ZIP 炸弹、加密文档、扩展名伪装与超大表格/幻灯片。
 */
class DocumentParseSecurityTest {

    private static final int MIB = 1024 * 1024;

    private final DocxTextParser docxParser = new DocxTextParser();
    private final PptxTextParser pptxParser = new PptxTextParser();
    private final XlsxTextParser xlsxParser = new XlsxTextParser();

    @Test
    void rejects_zip_entry_larger_than_limit() throws IOException {
        byte[] bomb = zipWithEntry("word/document.xml", new byte[MIB]);
        ParseLimits limits = limits(10_000, 1024L, 256L * MIB);

        assertThatThrownBy(() -> docxParser.parse(bomb, limits))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("解压");
    }

    @Test
    void rejects_zip_with_too_many_entries() throws IOException {
        byte[] zip = zipWithEntries(5);
        ParseLimits limits = limits(2, 64L * MIB, 256L * MIB);

        assertThatThrownBy(() -> docxParser.parse(zip, limits))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("条目");
    }

    @Test
    void rejects_zip_whose_declared_total_exceeds_limit() throws IOException {
        byte[] zip = zipWithEntry("word/document.xml", new byte[2 * MIB]);
        ParseLimits limits = limits(10_000, 64L * MIB, MIB);

        assertThatThrownBy(() -> docxParser.parse(zip, limits))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("解压");
    }

    @Test
    void rejects_encrypted_docx_with_chinese_message() throws Exception {
        byte[] encrypted = encryptedDocx();

        assertThatThrownBy(() -> docxParser.parse(encrypted, defaultLimits()))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("加密");
    }

    @Test
    void rejects_ole2_document_renamed_to_docx() {
        byte[] ole2 = new byte[512];
        byte[] magic = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(magic, 0, ole2, 0, magic.length);

        assertThatThrownBy(() -> docxParser.parse(ole2, defaultLimits()))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("docx");
    }

    @Test
    void rejects_docx_missing_main_part() throws IOException {
        byte[] zip = zipWithEntry("readme.txt", "hello".getBytes());

        assertThatThrownBy(() -> docxParser.parse(zip, defaultLimits()))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void rejects_xlsx_with_too_many_rows() throws IOException {
        byte[] xlsx = xlsxWithRows(5);
        ParseLimits limits = new ParseLimits(5_000_000, 2, 500_000, 200, 256, 10_000,
                100_000, 2_000, 5_000, 10_000, 67_108_864L, 268_435_456L, true, false,
                ParseLimits.LimitPolicy.FAIL);

        assertThatThrownBy(() -> xlsxParser.parse(xlsx, limits))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("行数");
    }

    @Test
    void rejects_pptx_with_too_many_slides() throws IOException {
        byte[] pptx = pptxWithSlides(3);
        ParseLimits limits = new ParseLimits(5_000_000, 50_000, 500_000, 200, 256, 10_000,
                100_000, 2, 5_000, 10_000, 67_108_864L, 268_435_456L, true, false,
                ParseLimits.LimitPolicy.FAIL);

        assertThatThrownBy(() -> pptxParser.parse(pptx, limits))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("幻灯片");
    }

    private ParseLimits defaultLimits() {
        return limits(10_000, 67_108_864L, 268_435_456L);
    }

    private ParseLimits limits(int maxZipEntries, long maxEntryBytes, long maxTotalBytes) {
        return new ParseLimits(5_000_000, 50_000, 500_000, 200, 256, 10_000, 100_000,
                2_000, 5_000, maxZipEntries, maxEntryBytes, maxTotalBytes, true, false,
                ParseLimits.LimitPolicy.FAIL);
    }

    private byte[] zipWithEntry(String name, byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private byte[] zipWithEntries(int count) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int i = 0; i < count; i++) {
                zip.putNextEntry(new ZipEntry("part-" + i + ".xml"));
                zip.write(("<x>" + i + "</x>").getBytes());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] encryptedDocx() throws Exception {
        byte[] plain;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("secret");
            document.write(output);
            plain = output.toByteArray();
        }
        try (POIFSFileSystem fileSystem = new POIFSFileSystem()) {
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword("pw");
            try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(plain));
                 OutputStream data = encryptor.getDataStream(fileSystem)) {
                pkg.save(data);
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                fileSystem.writeFilesystem(output);
                return output.toByteArray();
            }
        }
    }

    private byte[] xlsxWithRows(int rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("sheet");
            for (int i = 0; i < rows; i++) {
                XSSFRow row = sheet.createRow(i);
                row.createCell(0).setCellValue("row-" + i);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] pptxWithSlides(int slides) throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < slides; i++) {
                XSLFSlide slide = slideShow.createSlide();
                XSLFTextBox box = slide.createTextBox();
                box.setText("slide-" + i);
            }
            slideShow.write(output);
            return output.toByteArray();
        }
    }
}
