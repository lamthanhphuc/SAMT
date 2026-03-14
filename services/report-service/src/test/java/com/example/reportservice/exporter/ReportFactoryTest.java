package com.example.reportservice.exporter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportFactoryTest {

    @Test
    void getShouldResolveExporterIgnoringCase() {
        IReportExporter pdfExporter = new TestExporter("PDF");
        IReportExporter docxExporter = new TestExporter("DOCX");

        ReportFactory factory = new ReportFactory(List.of(pdfExporter, docxExporter));

        assertThat(factory.get("pdf")).isSameAs(pdfExporter);
        assertThat(factory.get("DoCx")).isSameAs(docxExporter);
    }

    @Test
    void getShouldThrowForUnsupportedType() {
        ReportFactory factory = new ReportFactory(List.of(new TestExporter("PDF")));

        assertThatThrownBy(() -> factory.get("XLSX"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported type");
    }

    private static final class TestExporter implements IReportExporter {
        private final String type;

        private TestExporter(String type) {
            this.type = type;
        }

        @Override
        public String export(String content) {
            return "output." + type.toLowerCase();
        }

        @Override
        public String getType() {
            return type;
        }
    }
}
