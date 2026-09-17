package org.mate.mate10.service.Impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.mate.mate10.config.TesseractConfig;
import org.mate.mate10.service.FileParseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileInputStream;

@Slf4j
@Service
public class FileParseServiceImpl implements FileParseService {

    @Autowired
    private TesseractConfig tesseractConfig;

    @Override
    public String parseFileContent(String filePath, String fileType) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        switch (fileType.toUpperCase()) {
            case "PDF":
                return parsePdf(filePath);
            case "IMAGE":
                return parseImage(filePath);
            case "WORD":
                return parseWord(filePath);
            case "EXCEL":
                return parseExcel(filePath);
            default:
                return "";
        }
    }

    //pdf解析
    private String parsePdf(String filePath) {
        try {
            //加载PDF
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                log.warn("PDF 文件不存在: {}", filePath);
                return "";
            }
            //使用PdfBOX解析
            try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(file)) {
                //提取文字
                org.apache.pdfbox.text.PDFTextStripper stripper =
                        new org.apache.pdfbox.text.PDFTextStripper();
                //起始页和结束页
                stripper.setStartPage(1);
                stripper.setEndPage(document.getNumberOfPages());
                //获取文字内容
                String text = stripper.getText(document);

                log.info("PDF 解析成功, 页数={}, 文字长度={}", document.getNumberOfPages(), text.length());

                return text;
            }
        } catch (Exception e) {
            log.error("PDF 解析失败: {}", filePath, e);
            return "";
        }
    }

    //图片 OCR
    private String parseImage(String filePath) {
        try {
            //检查文件存在
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                log.warn("图片不存在: {}", filePath);
                return "";
            }
            //检查Tesseract配置
            if (tesseractConfig.getPath() == null || tesseractConfig.getPath().isEmpty()) {
                log.error("Tesseract 路径未配置，无法执行 OCR");
                return "";
            }
            //创建Tesseract实例
            net.sourceforge.tess4j.Tesseract tesseract = new net.sourceforge.tess4j.Tesseract();
            tesseract.setDatapath(tesseractConfig.getDataPath());
            tesseract.setLanguage(tesseractConfig.getLanguage());
            //执行OCR识别
            String result = tesseract.doOCR(file);
            log.info("图片 OCR 成功, 文字长度={}", result.length());
            return result;
        } catch (Exception e) {
            log.error("图片 OCR 失败: {}", filePath, e);
            return "";
        }
    }

    //Word解析
    private String parseWord(String filePath) {
        try {
            HWPFDocument document = new HWPFDocument(new FileInputStream(filePath));
            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();
            extractor.close();
            document.close();
            return text != null ? text : "";
        } catch (Exception e) {
            log.error("Word 解析失败: {}", filePath, e);
            return "";
        }
    }

    //Excel解析
    private String parseExcel(String filePath) {
        try {
            Workbook workbook = WorkbookFactory.create(new FileInputStream(filePath));
            StringBuilder result = new StringBuilder();

            for (int sheetNum = 0; sheetNum < workbook.getNumberOfSheets(); sheetNum++) {
                Sheet sheet = workbook.getSheetAt(sheetNum);
                result.append("Sheet ").append(sheetNum + 1).append(":\n");

                for (Row row : sheet) {
                    for (Cell cell : row) {
                        switch (cell.getCellType()) {
                            case STRING:
                                result.append(cell.getStringCellValue()).append("\t");
                                break;
                            case NUMERIC:
                                result.append(cell.getNumericCellValue()).append("\t");
                                break;
                            case BOOLEAN:
                                result.append(cell.getBooleanCellValue()).append("\t");
                                break;
                            case FORMULA:
                                result.append(cell.getCellFormula()).append("\t");
                                break;
                            default:
                                result.append("\t");
                        }
                    }
                    result.append("\n");
                }
                result.append("\n");
            }

            workbook.close();
            return result.toString();
        } catch (Exception e) {
            log.error("Excel 解析失败: {}", filePath, e);
            return "";
        }
    }
}