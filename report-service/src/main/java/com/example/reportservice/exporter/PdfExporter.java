package com.example.reportservice.exporter;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.UUID;

@Component
public class PdfExporter implements IReportExporter {

    @Override
    public String export(String content) {

        // Tạo folder nếu chưa tồn tại
        new File("reports").mkdirs();

        String filePath =
                "reports/srs_" + UUID.randomUUID() + ".pdf";

        try (PdfWriter writer = new PdfWriter(filePath);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            document.add(new Paragraph(content));

        } catch (Exception e) {
            throw new RuntimeException("Error exporting PDF", e);
        }

        return filePath;
    }

    @Override
    public String getType() {
        return "PDF";
    }
}