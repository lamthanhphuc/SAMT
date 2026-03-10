package com.example.reportservice.exporter;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ReportFactory {

    private final Map<String, IReportExporter> exporters;

    public ReportFactory(List<IReportExporter> exporterList) {

        exporters = exporterList.stream()
                .collect(Collectors.toMap(
                        e -> e.getType().toUpperCase(),
                        e -> e
                ));
    }

    public IReportExporter get(String type) {

        IReportExporter exporter =
                exporters.get(type.toUpperCase());

        if (exporter == null) {
            throw new IllegalArgumentException(
                    "Unsupported type: " + type);
        }

        return exporter;
    }
}