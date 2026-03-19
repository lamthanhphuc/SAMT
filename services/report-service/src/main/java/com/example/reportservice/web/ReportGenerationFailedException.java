package com.example.reportservice.web;

import lombok.Getter;

import java.util.List;

@Getter
public class ReportGenerationFailedException extends RuntimeException {

    private final String step;
    private final String reason;
    private final List<String> logs;

    public ReportGenerationFailedException(String step, String reason, List<String> logs) {
        super(reason);
        this.step = step;
        this.reason = reason;
        this.logs = logs;
    }
}
