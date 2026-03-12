package com.example.reportservice.service;

import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.ReportMetadataResponse;
import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.entity.Report;
import com.example.reportservice.entity.ReportType;
import com.example.reportservice.exporter.IReportExporter;
import com.example.reportservice.exporter.ReportFactory;
import com.example.reportservice.grpc.IssueResponse;
import com.example.reportservice.grpc.SyncGrpcClient;
import com.example.reportservice.web.BadRequestException;
import com.example.reportservice.repository.ReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportingService {

        private static final String REPORT_STATUS_COMPLETED = "COMPLETED";
        private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final SyncGrpcClient syncClient;
    private final RawSrsBuilder rawBuilder;
    private final AiClient aiClient;
    private final ReportFactory reportFactory;
    private final ReportRepository reportRepository;
        private final TransactionTemplate transactionTemplate;

    public ReportResponse generate(
            Long projectConfigId,
            String subject,
            boolean useAi,
            String exportType) {

                if (projectConfigId == null || projectConfigId <= 0) {
                        throw new BadRequestException("projectConfigId must be a positive number");
                }

                if (subject == null || subject.isBlank()) {
                        throw new BadRequestException("Authenticated subject is required");
                }

        UUID createdBy = toCreatedBy(subject);

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
                        LocalDateTime createdAt = LocalDateTime.now();
            Report report = Report.builder()
                    .projectConfigId(projectConfigId)
                    .type(ReportType.SRS)
                    .filePath(filePath)
                                        .createdBy(createdBy)
                                        .createdAt(createdAt)
                    .build();

            reportRepository.save(report);

                        return toGenerationResponse(report);
        });
    }

        public ReportMetadataResponse getReport(UUID reportId) {
                return toMetadataResponse(findReport(reportId));
        }

        public PageResponse<ReportMetadataResponse> listReports(Long projectConfigId, String type, UUID createdBy, int page, int size) {
                Specification<Report> specification = (root, query, builder) -> builder.conjunction();

                if (projectConfigId != null) {
                        specification = specification.and((root, query, builder) -> builder.equal(root.get("projectConfigId"), projectConfigId));
                }

                if (type != null && !type.isBlank()) {
                        ReportType reportType = parseReportType(type);
                        specification = specification.and((root, query, builder) -> builder.equal(root.get("type"), reportType));
                }

                if (createdBy != null) {
                        specification = specification.and((root, query, builder) -> builder.equal(root.get("createdBy"), createdBy));
                }

                Page<Report> reportPage = reportRepository.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
                );

                return PageResponse.<ReportMetadataResponse>builder()
                        .content(reportPage.getContent().stream().map(this::toMetadataResponse).toList())
                        .page(page)
                        .size(size)
                        .totalElements(reportPage.getTotalElements())
                        .totalPages(reportPage.getTotalPages())
                        .build();
        }

        public ReportDownload loadReportDownload(UUID reportId) {
                Report report = findReport(reportId);
                Path reportPath = Path.of(report.getFilePath()).toAbsolutePath().normalize();
                if (!Files.exists(reportPath) || !Files.isRegularFile(reportPath)) {
                        throw new EntityNotFoundException("Report file not found");
                }

                try {
                        Resource resource = new UrlResource(reportPath.toUri());
                        if (!resource.exists() || !resource.isReadable()) {
                                throw new EntityNotFoundException("Report file not found");
                        }

                        String fileName = reportPath.getFileName().toString();
                        return new ReportDownload(resource, fileName, resolveMediaType(fileName));
                } catch (MalformedURLException ex) {
                        throw new EntityNotFoundException("Report file not found");
                }
        }

        private Report findReport(UUID reportId) {
                return reportRepository.findById(reportId)
                        .orElseThrow(() -> new EntityNotFoundException("Report not found"));
        }

        private ReportResponse toGenerationResponse(Report report) {
                return new ReportResponse(
                        report.getReportId(),
                        REPORT_STATUS_COMPLETED,
                        report.getCreatedAt(),
                        buildDownloadUrl(report.getReportId())
                );
        }

        private ReportMetadataResponse toMetadataResponse(Report report) {
                return ReportMetadataResponse.builder()
                        .reportId(report.getReportId())
                        .projectConfigId(report.getProjectConfigId())
                        .type(report.getType().name())
                        .createdBy(report.getCreatedBy())
                        .createdAt(report.getCreatedAt())
                        .status(REPORT_STATUS_COMPLETED)
                        .fileName(Path.of(report.getFilePath()).getFileName().toString())
                        .downloadUrl(buildDownloadUrl(report.getReportId()))
                        .build();
        }

        private String buildDownloadUrl(UUID reportId) {
                return "/api/reports/" + reportId + "/download";
        }

        private ReportType parseReportType(String value) {
                try {
                        return ReportType.valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                        throw new BadRequestException("Unsupported report type: " + value);
                }
        }

        private UUID toCreatedBy(String subject) {
                try {
                        return UUID.fromString(subject);
                } catch (IllegalArgumentException ignored) {
                        return UUID.nameUUIDFromBytes(("user:" + subject).getBytes(StandardCharsets.UTF_8));
                }
        }

        private MediaType resolveMediaType(String fileName) {
                String lowerCaseName = fileName.toLowerCase();
                if (lowerCaseName.endsWith(".pdf")) {
                        return MediaType.APPLICATION_PDF;
                }
                if (lowerCaseName.endsWith(".docx")) {
                        return DOCX_MEDIA_TYPE;
                }
                return MediaType.APPLICATION_OCTET_STREAM;
        }

        public record ReportDownload(Resource resource, String fileName, MediaType mediaType) {}
}