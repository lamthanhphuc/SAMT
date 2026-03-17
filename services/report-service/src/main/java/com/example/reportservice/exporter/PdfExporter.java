package com.example.reportservice.exporter;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class PdfExporter implements IReportExporter {

    @Override
    public String export(String content) {
        String filePath;
        try {
            Path reportsDir = Path.of(System.getProperty("java.io.tmpdir"), "samt-reports");
            Files.createDirectories(reportsDir);
            filePath = reportsDir.resolve("srs_" + UUID.randomUUID() + ".pdf").toString();
        } catch (Exception e) {
            throw new RuntimeException("Error preparing output directory", e);
        }

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