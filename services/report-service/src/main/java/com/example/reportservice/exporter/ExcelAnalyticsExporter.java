package com.example.reportservice.exporter;

import com.example.reportservice.service.analyzer.CommitAnalyzer;
import com.example.reportservice.service.analyzer.WorkDistributionAnalyzer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Component
public class ExcelAnalyticsExporter {

    public String exportWorkDistribution(List<WorkDistributionAnalyzer.MemberWorkDistribution> workDistribution) {
        return export(workDistribution, List.of(), true, false, "work_distribution");
    }

    public String exportCommitAnalysis(List<CommitAnalyzer.MemberCommitAnalysis> commitAnalysis) {
        return export(List.of(), commitAnalysis, false, true, "commit_analysis");
    }

    private String export(List<WorkDistributionAnalyzer.MemberWorkDistribution> workDistribution,
                         List<CommitAnalyzer.MemberCommitAnalysis> commitAnalysis,
                         boolean includeWorkDistribution,
                         boolean includeCommitAnalysis,
                         String filePrefix) {

        String filePath;
        try {
            Path reportsDir = Path.of(System.getProperty("java.io.tmpdir"), "samt-reports");
            Files.createDirectories(reportsDir);
            filePath = reportsDir.resolve(filePrefix + "_" + UUID.randomUUID() + ".xlsx").toString();
        } catch (Exception e) {
            throw new RuntimeException("Error preparing output directory", e);
        }

        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(filePath)) {

            DataFormat dataFormat = workbook.createDataFormat();
            CellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(dataFormat.getFormat("0.00%"));

            CellStyle twoDecimalStyle = workbook.createCellStyle();
            twoDecimalStyle.setDataFormat(dataFormat.getFormat("0.00"));

            if (includeWorkDistribution) {
                var sheet1 = workbook.createSheet("Work Distribution");
                writeWorkDistributionSheet(sheet1, workDistribution, percentStyle, twoDecimalStyle);
                autosize(sheet1, 6);
            }

            if (includeCommitAnalysis) {
                var sheet2 = workbook.createSheet("Commit Analysis");
                writeCommitAnalysisSheet(sheet2, commitAnalysis, twoDecimalStyle);
                autosize(sheet2, 5);
            }

            workbook.write(out);
        } catch (Exception e) {
            throw new RuntimeException("Error exporting XLSX", e);
        }

        return filePath;
    }

    private void writeWorkDistributionSheet(org.apache.poi.ss.usermodel.Sheet sheet,
                                            List<WorkDistributionAnalyzer.MemberWorkDistribution> rows,
                                            CellStyle percentStyle,
                                            CellStyle twoDecimalStyle) {

        int r = 0;
        Row header = sheet.createRow(r++);
        header.createCell(0).setCellValue("Member");
        header.createCell(1).setCellValue("Assigned");
        header.createCell(2).setCellValue("Completed");
        header.createCell(3).setCellValue("Completion Rate");
        header.createCell(4).setCellValue("Overdue");
        header.createCell(5).setCellValue("Avg Completion (days)");

        for (WorkDistributionAnalyzer.MemberWorkDistribution item : rows) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(item.memberName() == null ? "" : item.memberName());
            row.createCell(1).setCellValue(item.assigned());
            row.createCell(2).setCellValue(item.completed());

            Cell completionRate = row.createCell(3);
            completionRate.setCellValue(item.completionRate());
            completionRate.setCellStyle(percentStyle);

            row.createCell(4).setCellValue(item.overdue());

            Cell avgDays = row.createCell(5);
            avgDays.setCellValue(item.avgCompletionDays());
            avgDays.setCellStyle(twoDecimalStyle);
        }
    }

    private void writeCommitAnalysisSheet(org.apache.poi.ss.usermodel.Sheet sheet,
                                         List<CommitAnalyzer.MemberCommitAnalysis> rows,
                                         CellStyle twoDecimalStyle) {

        int r = 0;
        Row header = sheet.createRow(r++);
        header.createCell(0).setCellValue("Member");
        header.createCell(1).setCellValue("Commits");
        header.createCell(2).setCellValue("Active Days");
        header.createCell(3).setCellValue("Avg Commit Size");
        header.createCell(4).setCellValue("Score (0-10, heuristic)");

        for (CommitAnalyzer.MemberCommitAnalysis item : rows) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(item.memberName() == null ? "" : item.memberName());
            row.createCell(1).setCellValue(item.commits());
            row.createCell(2).setCellValue(item.activeDays());

            Cell avgSize = row.createCell(3);
            avgSize.setCellValue(item.avgCommitSize());
            avgSize.setCellStyle(twoDecimalStyle);

            Cell score = row.createCell(4);
            score.setCellValue(item.score());
            score.setCellStyle(twoDecimalStyle);
        }
    }

    private void autosize(org.apache.poi.ss.usermodel.Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}

