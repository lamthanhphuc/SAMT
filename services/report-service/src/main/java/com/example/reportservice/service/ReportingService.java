package com.example.reportservice.service;

import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.ReportMetadataResponse;
import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.entity.Report;
import com.example.reportservice.entity.ReportType;
import com.example.reportservice.exporter.IReportExporter;
import com.example.reportservice.exporter.ReportFactory;
import com.example.reportservice.grpc.GithubCommitResponse;
import com.example.reportservice.grpc.IssueResponse;
import com.example.reportservice.grpc.SyncGrpcClient;
import com.example.reportservice.grpc.UnifiedActivityResponse;
import com.example.reportservice.web.BadRequestException;
import com.example.reportservice.repository.ReportRepository;
import com.example.reportservice.repository.SyncJobRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

        private static final String REPORT_STATUS_COMPLETED = "COMPLETED";
        private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        private static final int MIN_EVIDENCE_THRESHOLD = 5;
        private static final List<String> SRS_RELEVANT_SYNC_TYPES = List.of("JIRA_ISSUES", "GITHUB_COMMITS");
        private static final String DEGRADED_STATUS = "PARTIAL_FAILURE";

    private final SyncGrpcClient syncClient;
    private final RawSrsBuilder rawBuilder;
    private final AiClient aiClient;
    private final ReportFactory reportFactory;
    private final ReportRepository reportRepository;
        private final SyncJobRepository syncJobRepository;
        private final TransactionTemplate transactionTemplate;

    public ReportResponse generate(
            String projectConfigId,
            String subject,
            boolean useAi,
            String exportType) {

                if (projectConfigId == null || projectConfigId.isBlank()) {
                        throw new BadRequestException("projectConfigId is required");
                }

                if (subject == null || subject.isBlank()) {
                        throw new BadRequestException("Authenticated subject is required");
                }

        UUID createdBy = toCreatedBy(subject);

        String normalizedProjectConfigId = projectConfigId.trim();

        List<IssueResponse> issues = fetchIssuesByProjectConfigId(normalizedProjectConfigId);

        if (issues.isEmpty()) {
                        throw new BadRequestException("No issues found for project configuration");
        }

        UUID uuidProjectConfigId = tryParseUuid(normalizedProjectConfigId);
        List<GithubCommitResponse> commits = uuidProjectConfigId == null
                ? List.of()
                : syncClient.getGithubCommits(uuidProjectConfigId);
        List<UnifiedActivityResponse> activities = uuidProjectConfigId == null
                ? List.of()
                : syncClient.getUnifiedActivities(uuidProjectConfigId);

        List<EvidenceBlock> evidenceBlocks = buildEvidenceBlocks(issues, commits, activities);
        evidenceBlocks.sort(Comparator
                .comparing(this::extractTimestampOrMin, Comparator.reverseOrder())
                .thenComparing(EvidenceBlock::sourceId, Comparator.nullsLast(String::compareTo)));

        // 2️⃣ Build raw SRS
        String rawText = rawBuilder.build(evidenceBlocks);

        // 3️⃣ AI enhancement
        String finalText = rawText;
        if (useAi) {
                if (isDataInsufficient(uuidProjectConfigId, evidenceBlocks)) {
                        throw new BadRequestException("INSUFFICIENT_DATA: Not enough high-quality evidence to generate SRS");
                }
                try {
                        finalText = aiClient.generateSrs(rawText);
                } catch (AiClient.TransientAiUpstreamException ex) {
                        log.warn("AI service temporarily unavailable, fallback to raw SRS content", ex);
                        finalText = rawText;
                }
        }

        // 4️⃣ Export
        IReportExporter exporter =
                reportFactory.get(exportType);

        String filePath =
                exporter.export(finalText);

        return transactionTemplate.execute(status -> {
                        LocalDateTime createdAt = LocalDateTime.now();
            Report report = Report.builder()
                    .projectConfigId(normalizedProjectConfigId)
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

        public PageResponse<ReportMetadataResponse> listReports(String projectConfigId, String type, UUID createdBy, int page, int size) {
                Specification<Report> specification = (root, query, builder) -> builder.conjunction();

                if (projectConfigId != null && !projectConfigId.isBlank()) {
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

        private List<IssueResponse> fetchIssuesByProjectConfigId(String projectConfigId) {
                try {
                        return syncClient.getIssues(UUID.fromString(projectConfigId));
                } catch (IllegalArgumentException ignored) {
                }

                try {
                        long numericId = Long.parseLong(projectConfigId);
                        if (numericId <= 0) {
                                throw new NumberFormatException("projectConfigId must be positive");
                        }
                        return syncClient.getIssues(numericId);
                } catch (NumberFormatException ex) {
                        throw new BadRequestException("projectConfigId must be a valid UUID or positive number");
                }
        }

        private boolean isDataInsufficient(UUID projectConfigId, List<EvidenceBlock> evidenceBlocks) {
                boolean notEnoughEvidence = evidenceBlocks.size() < MIN_EVIDENCE_THRESHOLD;
                boolean degraded = projectConfigId != null && syncJobRepository
                        .existsByProjectConfigIdAndJobTypeInAndStatus(projectConfigId, SRS_RELEVANT_SYNC_TYPES, DEGRADED_STATUS);
                return degraded || notEnoughEvidence;
        }

        private UUID tryParseUuid(String value) {
                try {
                        return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                        return null;
                }
        }

        private List<EvidenceBlock> buildEvidenceBlocks(
                List<IssueResponse> issues,
                List<GithubCommitResponse> commits,
                List<UnifiedActivityResponse> activities
        ) {
                List<EvidenceBlock> result = new ArrayList<>();

                for (IssueResponse issue : issues) {
                        String sourceId = firstNonBlank(issue.getIssueKey(), issue.getIssueId(), "ISSUE-UNKNOWN");
                        result.add(EvidenceBlock.builder()
                                .sourceType("ISSUE")
                                .sourceId(sourceId)
                                .summary(defaultText(issue.getSummary(), sourceId))
                                .description(defaultText(issue.getDescription(), issue.getSummary()))
                                .status(defaultText(issue.getStatus(), "UNKNOWN"))
                                .timestamp(firstNonBlank(issue.getUpdatedAt(), issue.getCreatedAt()))
                                .build());
                }

                for (GithubCommitResponse commit : commits) {
                        String sourceId = firstNonBlank(commit.getCommitSha(), "COMMIT-UNKNOWN");
                        String summary = firstLine(defaultText(commit.getMessage(), sourceId));
                        result.add(EvidenceBlock.builder()
                                .sourceType("COMMIT")
                                .sourceId(sourceId)
                                .summary(summary)
                                .description(defaultText(commit.getMessage(), summary))
                                .status("COMMITTED")
                                .timestamp(commit.getCommittedDate())
                                .build());
                }

                for (UnifiedActivityResponse activity : activities) {
                        String sourceId = firstNonBlank(activity.getExternalId(), "ACTIVITY-UNKNOWN");
                        result.add(EvidenceBlock.builder()
                                .sourceType("ACTIVITY")
                                .sourceId(sourceId)
                                .summary(defaultText(activity.getTitle(), sourceId))
                                .description(defaultText(activity.getDescription(), activity.getTitle()))
                                .status(defaultText(activity.getStatus(), "UNKNOWN"))
                                .timestamp(firstNonBlank(activity.getUpdatedAt(), activity.getCreatedAt()))
                                .build());
                }

                return result;
        }

        private LocalDateTime extractTimestampOrMin(EvidenceBlock evidenceBlock) {
                if (evidenceBlock.timestamp() == null || evidenceBlock.timestamp().isBlank()) {
                        return LocalDateTime.MIN;
                }

                String timestamp = evidenceBlock.timestamp().trim();
                try {
                        return LocalDateTime.parse(timestamp);
                } catch (DateTimeParseException ignored) {
                }

                try {
                        return OffsetDateTime.parse(timestamp).toLocalDateTime();
                } catch (DateTimeParseException ignored) {
                }

                return LocalDateTime.MIN;
        }

        private String defaultText(String value, String fallback) {
                String normalized = normalize(value);
                return normalized != null ? normalized : firstNonBlank(fallback, "N/A");
        }

        private String firstLine(String value) {
                String normalized = normalize(value);
                if (normalized == null) {
                        return "N/A";
                }
                int index = normalized.indexOf('\n');
                return index >= 0 ? normalized.substring(0, index).trim() : normalized;
        }

        private String firstNonBlank(String... values) {
                for (String value : values) {
                        String normalized = normalize(value);
                        if (normalized != null) {
                                return normalized;
                        }
                }
                return null;
        }

        private String normalize(String value) {
                if (value == null) {
                        return null;
                }
                String trimmed = value.trim();
                return trimmed.isEmpty() ? null : trimmed;
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