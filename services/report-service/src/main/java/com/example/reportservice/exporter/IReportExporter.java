package com.example.reportservice.exporter;

public interface IReportExporter {

    String export(String content);

    String getType();
}
