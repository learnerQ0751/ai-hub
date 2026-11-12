package com.qtua.aihub.integration.rag.parse;

import com.qtua.aihub.integration.rag.DocumentParseException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Excel（.xlsx）文本解析器（REQ-3.9）。
 *
 * <p>按工作表输出 TSV 风格文本，保留表头；公式优先取缓存值，无缓存回退公式文本。
 * 隐藏工作表默认跳过；行数、列数、单元格数均受配置上限约束，超限按 fail 策略拒绝。
 * 图表、图片、批注不在本期提取范围。</p>
 */
@Component
public class XlsxTextParser implements DocumentTextParser {

    private static final Set<String> EXTENSIONS = Set.of("xlsx");
    private static final String ENCRYPTED_MESSAGE = "不支持加密文档，请先解除密码后再上传";
    private static final String NOT_XLSX_MESSAGE =
            "文件不是有效的 xlsx，请确认文件格式（老 .xls 请另存为 .xlsx）";
    private static final String MISMATCH_MESSAGE = "文件内容与扩展名不一致（期望 xlsx）";
    private static final String CORRUPT_MESSAGE = "文件格式损坏或与扩展名不符（xlsx）";

    /**
     * 构造解析器并应用 POI 全局安全限制。
     */
    public XlsxTextParser() {
        PoiSecurityConfig.applyDefaults();
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String formatName() {
        return "xlsx";
    }

    @Override
    public String parse(byte[] content, ParseLimits limits) {
        if (DocumentFormatDetector.isEncryptedOoxml(content)) {
            throw new DocumentParseException(ENCRYPTED_MESSAGE);
        }
        DocumentFormatDetector.OoxmlKind kind = DocumentFormatDetector.detectOoxml(content, limits);
        if (kind == DocumentFormatDetector.OoxmlKind.NOT_ZIP) {
            if (DocumentFormatDetector.isOle2(content)) {
                throw new DocumentParseException(NOT_XLSX_MESSAGE);
            }
            throw new DocumentParseException(MISMATCH_MESSAGE);
        }
        if (kind != DocumentFormatDetector.OoxmlKind.XLSX) {
            throw new DocumentParseException(MISMATCH_MESSAGE);
        }
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(content));
             XSSFWorkbook workbook = new XSSFWorkbook(pkg)) {
            return extract(workbook, limits);
        } catch (EncryptedDocumentException ex) {
            throw new DocumentParseException(ENCRYPTED_MESSAGE, ex);
        } catch (OLE2NotOfficeXmlFileException ex) {
            throw new DocumentParseException(NOT_XLSX_MESSAGE, ex);
        } catch (DocumentParseException ex) {
            throw ex;
        } catch (IOException | InvalidFormatException | RuntimeException ex) {
            throw new DocumentParseException(CORRUPT_MESSAGE, ex);
        }
    }

    private String extract(XSSFWorkbook workbook, ParseLimits limits) {
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        formatter.setUseCachedValuesForFormulaCells(true);
        StringBuilder out = new StringBuilder();
        int visibleSheets = 0;
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            if (workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index)) {
                continue;
            }
            visibleSheets++;
            if (visibleSheets > limits.maxSheets()) {
                throw new DocumentParseException(
                        "工作表数超过上限（" + limits.maxSheets() + "）", null);
            }
            Sheet sheet = workbook.getSheetAt(index);
            out.append("## 工作表: ").append(sheet.getSheetName()).append('\n');
            appendSheet(sheet, formatter, limits, out);
        }
        return out.toString();
    }

    private void appendSheet(Sheet sheet, DataFormatter formatter, ParseLimits limits,
                             StringBuilder out) {
        int rows = 0;
        long cells = 0L;
        for (Row row : sheet) {
            rows++;
            if (rows > limits.maxRowsPerSheet()) {
                throw new DocumentParseException(
                        "工作表行数超过上限（" + limits.maxRowsPerSheet() + "）", null);
            }
            int columns = row.getLastCellNum();
            if (columns > limits.maxColumns()) {
                throw new DocumentParseException(
                        "工作表列数超过上限（" + limits.maxColumns() + "）", null);
            }
            StringBuilder line = new StringBuilder();
            boolean hasText = false;
            for (int column = 0; column < columns; column++) {
                cells++;
                if (cells > limits.maxCellsPerSheet()) {
                    throw new DocumentParseException(
                            "工作表单元格数超过上限（" + limits.maxCellsPerSheet() + "）", null);
                }
                if (column > 0) {
                    line.append('\t');
                }
                Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String value = Objects.isNull(cell) ? "" : formatCell(cell, formatter);
                if (StringUtils.hasText(value)) {
                    hasText = true;
                }
                line.append(value.replace('\n', ' ').replace('\t', ' '));
            }
            if (hasText) {
                out.append(line).append('\n');
            }
        }
    }

    private String formatCell(Cell cell, DataFormatter formatter) {
        String value = formatter.formatCellValue(cell);
        if (cell.getCellType() == CellType.FORMULA && !StringUtils.hasText(value)) {
            return cell.getCellFormula();
        }
        return value;
    }
}
