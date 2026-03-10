package com.example.reportservice.service;

import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.entity.Report;
import com.example.reportservice.entity.ReportType;
import com.example.reportservice.exporter.IReportExporter;
import com.example.reportservice.exporter.ReportFactory;
import com.example.reportservice.grpc.IssueResponse;
import com.example.reportservice.grpc.SyncGrpcClient;
import com.example.reportservice.web.BadRequestException;
import com.example.reportservice.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final SyncGrpcClient syncClient;
    private final RawSrsBuilder rawBuilder;
    private final AiClient aiClient;
    private final ReportFactory reportFactory;
    private final ReportRepository reportRepository;
        private final TransactionTemplate transactionTemplate;

    public ReportResponse generate(
            Long projectConfigId,
            UUID userId,
            boolean useAi,
            String exportType) {

                if (projectConfigId == null || projectConfigId <= 0) {
                        throw new BadRequestException("projectConfigId must be a positive number");
                }

                if (userId == null) {
                        throw new BadRequestException("Authenticated subject is required");
                }

        // 1️⃣ Call Sync
        List<IssueResponse> issues =
                syncClient.getIssues(projectConfigId);

        if (issues.isEmpty()) {
                        throw new BadRequestException("No issues found for project configuration");
        }

        // 2️⃣ Build raw SRS
        String rawText = rawBuilder.build(issues);

        // 3️⃣ AI enhancement
        String finalText =
                useAi ? aiClient.generateSrs(rawText) : rawText;

        // 4️⃣ Export
        IReportExporter exporter =
                reportFactory.get(exportType);

        String filePath =
                exporter.export(finalText);

        return transactionTemplate.execute(status -> {
            Report report = Report.builder()
                    .projectConfigId(projectConfigId)
                    .type(ReportType.SRS)
                    .filePath(filePath)
                    .createdBy(userId)
                    .createdAt(LocalDateTime.now())
                    .build();

            reportRepository.save(report);

            return new ReportResponse(
                    report.getReportId(),
                    filePath,
                    "Report generated successfully"
            );
        });
    }
}