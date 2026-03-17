package com.example.reportservice.exporter;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class DocxExporter implements IReportExporter {

    @Override
    public String export(String content) {
        String filePath;
        try {
            Path reportsDir = Path.of(System.getProperty("java.io.tmpdir"), "samt-reports");
            Files.createDirectories(reportsDir);
            filePath = reportsDir.resolve("srs_" + UUID.randomUUID() + ".docx").toString();
        } catch (Exception e) {
            throw new RuntimeException("Error preparing output directory", e);
        }

        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(filePath)) {

            XWPFParagraph paragraph = doc.createParagraph();
            paragraph.createRun().setText(content);

            doc.write(out);

        } catch (Exception e) {
            throw new RuntimeException("Error exporting DOCX", e);
        }

        return filePath;
    }

    @Override
    public String getType() {
        return "DOCX";
    }
}