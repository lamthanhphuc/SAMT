package com.example.reportservice.exporter;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

@Component
public class DocxExporter implements IReportExporter {

    @Override
    public String export(String content) {

        new File("reports").mkdirs();

        String filePath =
                "reports/srs_" + UUID.randomUUID() + ".docx";

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