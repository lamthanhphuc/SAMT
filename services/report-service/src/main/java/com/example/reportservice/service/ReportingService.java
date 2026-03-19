package com.example.reportservice.service;

import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.ReportMetadataResponse;
import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.dto.ai.AiStructuredResponse;
import com.example.reportservice.entity.Report;
import com.example.reportservice.entity.ReportType;
import com.example.reportservice.exporter.IReportExporter;
import com.example.reportservice.exporter.ReportFactory;
import com.example.reportservice.grpc.GithubCommitResponse;
import com.example.reportservice.grpc.IssueResponse;
import com.example.reportservice.grpc.SyncGrpcClient;
import com.example.reportservice.grpc.UnifiedActivityResponse;
import com.example.reportservice.web.BadRequestException;
import com.example.reportservice.web.ReportGenerationFailedException;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

        private static final String REPORT_STATUS_COMPLETED = "COMPLETED";
        private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        private static final int MIN_EVIDENCE_THRESHOLD = 5;
        private static final List<String> SRS_RELEVANT_SYNC_TYPES = List.of("JIRA_ISSUES", "GITHUB_COMMITS");
        private static final String DEGRADED_STATUS = "PARTIAL_FAILURE";
        private static final int MAX_STEP_RETRIES = 3;
        private static final Pattern MEASURABLE_CRITERIA_PATTERN = Pattern.compile(
                "(?i)(?:\\b(?:under|within|at least|at most|less than|greater than|no more than)\\b[^\\n]{0,60}\\d)|(?:\\d+(?:\\.\\d+)?\\s*(?:ms|s|sec|seconds|min|minutes|hours|%|users|requests|req/s|qps|mb|gb|records))");
        private static final int MAX_EVIDENCE_SUMMARY_CHARS = 400;
        private static final int MAX_EVIDENCE_DESCRIPTION_CHARS = 4000;
        // Keep prompts small enough for local Ollama latency/timeout constraints.
        private static final int MAX_EVIDENCE_BLOCKS_FOR_AI = 40;

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

        List<String> logs = new ArrayList<>();

        if (projectConfigId == null || projectConfigId.isBlank()) {
            throw new BadRequestException("projectConfigId is required");
        }

        if (subject == null || subject.isBlank()) {
            throw new BadRequestException("Authenticated subject is required");
        }

        UUID createdBy = toCreatedBy(subject);
        String normalizedProjectConfigId = projectConfigId.trim();

        ReportRunContext ctx = new ReportRunContext(
                normalizedProjectConfigId,
                createdBy,
                useAi,
                exportType,
                logs
        );

        Step current = Step.INPUT;
        while (current != Step.DONE) {
            try {
                runStep(current, ctx);
                current = current.next();
            } catch (StepFailedException failure) {
                if (failure.attempt >= MAX_STEP_RETRIES) {
                    throw new ReportGenerationFailedException(
                            failure.step.name(),
                            failure.reason,
                            List.copyOf(logs)
                    );
                }

                Step retryFrom = applyFixAndPrepareRetry(failure, ctx);
                current = retryFrom;
            }
        }

        Report report = transactionTemplate.execute(status -> {
            LocalDateTime createdAt = LocalDateTime.now();
            Report entity = Report.builder()
                    .projectConfigId(normalizedProjectConfigId)
                    .type(ReportType.SRS)
                    .filePath(ctx.filePath)
                    .createdBy(createdBy)
                    .createdAt(createdAt)
                    .build();
            reportRepository.save(entity);
            return entity;
        });

        return toGenerationResponse(report);
    }

        private void runStep(Step step, ReportRunContext ctx) {
                reportLog(ctx, step, "START", "Starting step", stepContext(ctx, step));
                try {
                        switch (step) {
                                case INPUT -> ctx.results.put(Step.INPUT, inputStep(ctx));
                                case FETCH -> ctx.results.put(Step.FETCH, fetchStep(ctx, ctx.get(InputResult.class, Step.INPUT)));
                                case EVIDENCE_BUILD -> ctx.results.put(Step.EVIDENCE_BUILD, evidenceBuildStep(ctx, ctx.get(FetchResult.class, Step.FETCH)));
                                case RAW_BUILD -> ctx.results.put(Step.RAW_BUILD, rawBuildStep(ctx, ctx.get(EvidenceBuildResult.class, Step.EVIDENCE_BUILD)));
                                case AI_PROCESSING -> ctx.results.put(Step.AI_PROCESSING, aiProcessingStep(
                                        ctx,
                                        ctx.get(InputResult.class, Step.INPUT),
                                        ctx.get(EvidenceBuildResult.class, Step.EVIDENCE_BUILD),
                                        ctx.get(RawBuildResult.class, Step.RAW_BUILD)
                                ));
                                case VALIDATION -> ctx.results.put(Step.VALIDATION, validationStep(
                                        ctx,
                                        ctx.get(InputResult.class, Step.INPUT),
                                        ctx.get(AiProcessingResult.class, Step.AI_PROCESSING)
                                ));
                                case EXPORT -> ctx.results.put(Step.EXPORT, exportStep(
                                        ctx,
                                        ctx.get(InputResult.class, Step.INPUT),
                                        ctx.get(ValidationResult.class, Step.VALIDATION)
                                ));
                                case PERSIST -> ctx.results.put(Step.PERSIST, persistGuardStep(
                                        ctx,
                                        ctx.get(ExportResult.class, Step.EXPORT)
                                ));
                                default -> {
                                }
                        }
                        reportLog(ctx, step, "SUCCESS", "Completed step", stepContext(ctx, step));
                } catch (RuntimeException ex) {
                        String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                        int attempt = ctx.stepAttempts.merge(step, 1, Integer::sum);
                        reportLog(ctx, step, "FAIL", reason, stepContext(ctx, step));
                        throw new StepFailedException(step, attempt, reason, ex);
                }
        }

        private Step applyFixAndPrepareRetry(StepFailedException failure, ReportRunContext ctx) {
                Step step = failure.step;
                int attempt = failure.attempt;

                reportLog(ctx, step, "RETRY", "Applying fix before retry attempt " + (attempt + 1), stepContext(ctx, step));

                switch (step) {
                        case FETCH -> {
                                // Clear cached results and refetch
                                ctx.clearFrom(Step.FETCH);
                        }
                        case EVIDENCE_BUILD -> {
                                // Filter invalid entries (will be applied inside step); just clear cached result
                                ctx.clearFrom(Step.EVIDENCE_BUILD);
                        }
                        case RAW_BUILD -> {
                                // Prune unusable evidence then rebuild JSON
                                EvidenceBuildResult evidence = ctx.tryGet(EvidenceBuildResult.class, Step.EVIDENCE_BUILD);
                                if (evidence != null) {
                                        List<EvidenceBlock> pruned = evidence.evidenceBlocks().stream()
                                                .filter(this::isEvidenceUsable)
                                                .toList();
                                        ctx.results.put(Step.EVIDENCE_BUILD, new EvidenceBuildResult(pruned));
                                }
                                ctx.clearFrom(Step.RAW_BUILD);
                        }
                        case AI_PROCESSING -> {
                                // Regenerate with strict deterministic mode
                                ctx.aiStrict = true;
                                ctx.clearFrom(Step.AI_PROCESSING);
                        }
                        case VALIDATION -> {
                                // Remove invalid requirements + sort/dedup; next retry forces strict AI regenerate
                                AiProcessingResult ai = ctx.tryGet(AiProcessingResult.class, Step.AI_PROCESSING);
                                if (ai != null && ai.structured() != null && ai.structured().getRequirements() != null) {
                                        ai.structured().setRequirements(filterAndSortRequirements(ai.structured().getRequirements()));
                                }
                                ctx.aiStrict = true;
                                ctx.clearFrom(Step.AI_PROCESSING);
                                return Step.AI_PROCESSING;
                        }
                        case EXPORT -> {
                                // Export failures: fallback to DOCX
                                ctx.exportType = "DOCX";
                                ctx.clearFrom(Step.EXPORT);
                        }
                        case PERSIST -> {
                                // Persistence failures: nothing deterministic to fix; retry transaction only
                        }
                        default -> {
                        }
                }
                return step;
        }

        private InputResult inputStep(ReportRunContext ctx) {
                if (ctx.projectConfigId == null || ctx.projectConfigId.isBlank()) {
                        throw new BadRequestException("projectConfigId is required");
                }
                if (ctx.createdBy == null) {
                        throw new BadRequestException("createdBy is required");
                }
                UUID uuidProjectConfigId = tryParseUuid(ctx.projectConfigId);
                ctx.aiStrict = false;
                return new InputResult(ctx.projectConfigId, uuidProjectConfigId, ctx.createdBy, ctx.useAi, ctx.exportType);
        }

        private FetchResult fetchStep(ReportRunContext ctx, InputResult input) {
                List<IssueResponse> issues = fetchIssuesByProjectConfigId(input.projectConfigId());
                List<GithubCommitResponse> commits;
                List<UnifiedActivityResponse> activities;

                if (input.uuidProjectConfigId() == null) {
                        commits = List.of();
                        activities = List.of();
                } else {
                        commits = safeList(syncClient.getGithubCommits(input.uuidProjectConfigId()));
                        activities = safeList(syncClient.getUnifiedActivities(input.uuidProjectConfigId()));
                }

                if ((issues == null || issues.isEmpty())
                        && (commits == null || commits.isEmpty())
                        && (activities == null || activities.isEmpty())) {
                        throw new BadRequestException("No upstream data returned (issues/commits/activities are all empty)");
                }

                return new FetchResult(safeList(issues), safeList(commits), safeList(activities));
        }

        private EvidenceBuildResult evidenceBuildStep(ReportRunContext ctx, FetchResult fetch) {
                List<EvidenceBlock> evidenceBlocks = buildEvidenceBlocks(fetch.issues(), fetch.commits(), fetch.activities()).stream()
                        .filter(this::isEvidenceUsable)
                        .map(this::sanitizeEvidenceBlock)
                        .toList();

                if (evidenceBlocks.isEmpty()) {
                        throw new BadRequestException("No usable evidence blocks could be built");
                }

                List<EvidenceBlock> sorted = new ArrayList<>(evidenceBlocks);
                sorted.sort(Comparator
                        .comparing(this::extractTimestampOrMin, Comparator.reverseOrder())
                        .thenComparing(EvidenceBlock::sourceType, Comparator.nullsLast(String::compareTo))
                        .thenComparing(EvidenceBlock::sourceId, Comparator.nullsLast(String::compareTo)));

                // Cap the evidence set deterministically to keep AI prompts stable and avoid local OOM kills.
                // (Most recent evidence is already first due to reverse timestamp sort.)
                int beforeCap = sorted.size();
                if (beforeCap > MAX_EVIDENCE_BLOCKS_FOR_AI) {
                        sorted = new ArrayList<>(sorted.subList(0, MAX_EVIDENCE_BLOCKS_FOR_AI));
                        reportLog(ctx, Step.EVIDENCE_BUILD, "INFO", "Capped evidence blocks for stability", Map.of(
                                "before", beforeCap,
                                "after", sorted.size()
                        ));
                }

                return new EvidenceBuildResult(List.copyOf(sorted));
        }

        private RawBuildResult rawBuildStep(ReportRunContext ctx, EvidenceBuildResult evidence) {
                if (evidence.evidenceBlocks() == null || evidence.evidenceBlocks().isEmpty()) {
                        throw new BadRequestException("Evidence blocks are empty");
                }
                String rawJson = rawBuilder.build(evidence.evidenceBlocks());
                if (rawJson == null || rawJson.isBlank()) {
                        throw new BadRequestException("Raw evidence serialization returned empty output");
                }
                int bytes = rawJson.getBytes(StandardCharsets.UTF_8).length;
                return new RawBuildResult(rawJson, bytes);
        }

        private AiProcessingResult aiProcessingStep(ReportRunContext ctx, InputResult input, EvidenceBuildResult evidence, RawBuildResult raw) {
                if (!input.useAi()) {
                        String srs = buildNonAiSrs(input, evidence, "NON_AI");
                        if (srs == null || srs.isBlank()) {
                                throw new BadRequestException("Non-AI SRS generation returned empty output");
                        }

                        reportLog(ctx, Step.AI_PROCESSING, "SUCCESS", "Generated non-AI SRS (validation bypassed)", Map.of(
                                "aiStrict", false,
                                "evidenceBlocks", evidence == null || evidence.evidenceBlocks() == null ? 0 : evidence.evidenceBlocks().size()
                        ));
                        return new AiProcessingResult(false, null, srs, 0);
                }

                if (raw.rawEvidenceJson() == null || raw.rawEvidenceJson().isBlank()) {
                        throw new BadRequestException("Raw evidence JSON is empty");
                }

                if (isDataInsufficient(input.uuidProjectConfigId(), evidence.evidenceBlocks())) {
                        throw new BadRequestException("INSUFFICIENT_DATA: Not enough high-quality evidence to generate SRS");
                }

                AiStructuredResponse structured;
                String srs;
                try {
                        structured = aiClient.generateSrsStructured(raw.rawEvidenceJson(), ctx.aiStrict);
                        srs = structured == null ? null : structured.getSrsContent();
                } catch (Exception ex) {
                        // AI is a best-effort enhancement. When it is unavailable (503/401/schema errors),
                        // we fall back to a deterministic non-AI SRS so report generation still succeeds.
                        reportLog(ctx, Step.AI_PROCESSING, "FAIL", "AI generation failed; falling back to non-AI SRS", Map.of(
                                "aiStrict", ctx.aiStrict,
                                "error", ex.getClass().getSimpleName()
                        ));

                        String fallback = buildNonAiSrs(input, evidence, "AI_FALLBACK_NON_AI");
                        if (fallback == null || fallback.isBlank()) {
                                throw new BadRequestException("AI failed and non-AI fallback returned empty output");
                        }

                        reportLog(ctx, Step.AI_PROCESSING, "SUCCESS", "Generated non-AI SRS (AI fallback)", Map.of(
                                "aiStrict", false,
                                "evidenceBlocks", evidence == null || evidence.evidenceBlocks() == null ? 0 : evidence.evidenceBlocks().size()
                        ));
                        return new AiProcessingResult(false, null, fallback, 0);
                }

                if (structured == null || srs == null || srs.isBlank()) {
                        throw new BadRequestException("AI processing returned empty output");
                }

                int requirementsCount = structured.getRequirements() == null ? 0 : structured.getRequirements().size();
                reportLog(ctx, Step.AI_PROCESSING, "SUCCESS", "AI returned structured output", Map.of(
                        "aiStrict", ctx.aiStrict,
                        "requirements", requirementsCount
                ));

                return new AiProcessingResult(ctx.aiStrict, structured, srs, requirementsCount);
        }

        private ValidationResult validationStep(ReportRunContext ctx, InputResult input, AiProcessingResult ai) {
                if (!input.useAi()) {
                        if (ai == null || ai.srsContent() == null || ai.srsContent().isBlank()) {
                                throw new BadRequestException("Non-AI SRS content missing");
                        }
                        reportLog(ctx, Step.VALIDATION, "SUCCESS", "Validation bypassed (non-AI)", Map.of(
                                "requirements", 0
                        ));
                        return new ValidationResult(false, null, ai.srsContent(), 0);
                }
                // If AI failed earlier and we fell back to non-AI output, bypass validation.
                if (ai == null || ai.structured() == null) {
                        if (ai != null && ai.srsContent() != null && !ai.srsContent().isBlank()) {
                                reportLog(ctx, Step.VALIDATION, "SUCCESS", "Validation bypassed (AI fallback to non-AI)", Map.of(
                                        "requirements", 0
                                ));
                                return new ValidationResult(false, null, ai.srsContent(), 0);
                        }
                        throw new BadRequestException("AI result missing");
                }

                List<AiStructuredResponse.RequirementItem> requirements =
                        ai.structured().getRequirements() == null ? List.of() : ai.structured().getRequirements();

                requirements = filterAndSortRequirements(requirements);
                ai.structured().setRequirements(requirements);

                ValidationFailure validationFailure = validateStrict(requirements);
                if (validationFailure != null) {
                        reportLog(ctx, Step.VALIDATION, "FAIL", validationFailure.reason, validationFailure.context);
                        throw new BadRequestException(validationFailure.reason);
                }

                if (ai.srsContent() == null || ai.srsContent().isBlank()) {
                        throw new BadRequestException("SRS content is empty");
                }

                reportLog(ctx, Step.VALIDATION, "SUCCESS", "Validation passed", Map.of(
                        "requirements", requirements.size()
                ));
                return new ValidationResult(ai.aiStrict(), ai.structured(), ai.srsContent(), requirements.size());
        }

        private String buildNonAiSrs(InputResult input, EvidenceBuildResult evidence, String modeLabel) {
                List<EvidenceBlock> blocks = evidence == null || evidence.evidenceBlocks() == null ? List.of() : evidence.evidenceBlocks();
                if (blocks.isEmpty()) {
                        return "";
                }

                StringBuilder sb = new StringBuilder(16_384);
                sb.append("SOFTWARE REQUIREMENTS SPECIFICATION (SRS)\n");
                sb.append("ProjectConfigId: ").append(input.uuidProjectConfigId()).append("\n");
                sb.append("Mode: ").append(modeLabel == null || modeLabel.isBlank() ? "NON_AI" : modeLabel).append("\n\n");

                sb.append("## Evidence Summary\n");
                sb.append("- Evidence blocks: ").append(blocks.size()).append("\n\n");

                // Keep this report fully English by not embedding upstream evidence free-text (Jira/Git may be Vietnamese).
                sb.append("## Evidence References (Most Recent First)\n");
                int maxItems = Math.min(blocks.size(), 120);
                for (int i = 0; i < maxItems; i++) {
                        EvidenceBlock b = blocks.get(i);
                        sb.append("- [").append(safe(b.sourceType())).append("] ")
                                .append(safe(b.sourceId()))
                                .append("\n");
                }
                if (blocks.size() > maxItems) {
                        sb.append("- ... ").append(blocks.size() - maxItems).append(" more evidence items omitted\n");
                }
                sb.append("\n");

                sb.append("## Notes\n");
                if ("AI_FALLBACK_NON_AI".equalsIgnoreCase(modeLabel)) {
                        sb.append("AI was requested but the AI service was unavailable. This report contains a deterministic evidence snapshot and does not include validated SHALL requirements.\n");
                } else {
                        sb.append("This report was generated without AI. It contains a deterministic evidence snapshot and does not include validated SHALL requirements.\n");
                }
                return sb.toString();
        }

        private String safe(String value) {
                return value == null ? "" : value.trim();
        }

        private ExportResult exportStep(ReportRunContext ctx, InputResult input, ValidationResult validated) {
                if (validated.srsContent() == null || validated.srsContent().isBlank()) {
                        throw new BadRequestException("Nothing to export (empty SRS content)");
                }

                IReportExporter exporter = reportFactory.get(ctx.exportType);
                String filePath = exporter.export(validated.srsContent());
                if (filePath == null || filePath.isBlank()) {
                        throw new BadRequestException("Exporter returned empty file path");
                }

                Path reportPath = Path.of(filePath).toAbsolutePath().normalize();
                if (!Files.exists(reportPath) || !Files.isRegularFile(reportPath)) {
                        throw new BadRequestException("Export failed: file was not created");
                }

                String finalPath = reportPath.toString();
                reportLog(ctx, Step.EXPORT, "SUCCESS", "Exported report", Map.of(
                        "exportType", ctx.exportType,
                        "filePath", finalPath
                ));
                ctx.filePath = finalPath;
                return new ExportResult(ctx.exportType, finalPath);
        }

        private PersistGuardResult persistGuardStep(ReportRunContext ctx, ExportResult export) {
                if (export.filePath() == null || export.filePath().isBlank()) {
                        throw new BadRequestException("Missing exported filePath before persistence");
                }
                if (!Files.exists(Path.of(export.filePath())) || !Files.isRegularFile(Path.of(export.filePath()))) {
                        throw new BadRequestException("Export file missing before persistence");
                }
                return new PersistGuardResult(export.filePath());
        }

        private Map<String, Object> stepContext(ReportRunContext ctx, Step step) {
                int retries = ctx.stepAttempts.getOrDefault(step, 0);
                return switch (step) {
                        case INPUT -> Map.of(
                                "projectConfigId", ctx.projectConfigId,
                                "retries", retries,
                                "useAi", ctx.useAi,
                                "exportType", ctx.exportType
                        );
                        case FETCH -> Map.of(
                                "projectConfigId", ctx.projectConfigId,
                                "retries", retries,
                                "uuidProjectConfigId", ctx.tryUuidProjectConfigId(),
                                "issues", ctx.tryCountIssues(),
                                "commits", ctx.tryCountCommits(),
                                "activities", ctx.tryCountActivities()
                        );
                        case EVIDENCE_BUILD -> Map.of(
                                "retries", retries,
                                "evidenceBlocks", ctx.tryCountEvidenceBlocks()
                        );
                        case RAW_BUILD -> Map.of(
                                "retries", retries,
                                "rawEvidenceBytes", ctx.tryRawEvidenceBytes()
                        );
                        case AI_PROCESSING -> Map.of(
                                "retries", retries,
                                "aiStrict", ctx.aiStrict,
                                "rawEvidenceBytes", ctx.tryRawEvidenceBytes()
                        );
                        case VALIDATION -> Map.of(
                                "retries", retries,
                                "requirements", ctx.tryCountRequirements()
                        );
                        case EXPORT -> Map.of(
                                "retries", retries,
                                "exportType", ctx.exportType
                        );
                        case PERSIST -> Map.of(
                                "retries", retries,
                                "filePath", ctx.filePath
                        );
                        default -> Collections.emptyMap();
                };
        }

        private void reportLog(ReportRunContext ctx, Step step, String status, String message, Map<String, Object> context) {
                String contextStr = context == null || context.isEmpty()
                        ? ""
                        : context.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + String.valueOf(e.getValue()))
                        .reduce((a, b) -> a + " " + b)
                        .orElse("");

                String line = "[REPORT][" + step.name() + "][" + status + "] " + message + (contextStr.isBlank() ? "" : " | " + contextStr);
                ctx.logs.add(line);

                if ("FAIL".equals(status)) {
                        log.warn(line);
                } else if ("RETRY".equals(status)) {
                        log.info(line);
                } else {
                        log.info(line);
                }
        }

        private boolean isEvidenceUsable(EvidenceBlock block) {
                if (block == null) {
                        return false;
                }
                if (block.sourceType() == null || block.sourceType().isBlank()) {
                        return false;
                }
                if (block.sourceId() == null || block.sourceId().isBlank()) {
                        return false;
                }
                return (block.summary() != null && !block.summary().isBlank())
                        || (block.description() != null && !block.description().isBlank());
        }

        private EvidenceBlock sanitizeEvidenceBlock(EvidenceBlock block) {
                if (block == null) {
                        return null;
                }
                String summary = truncate(normalize(block.summary()), MAX_EVIDENCE_SUMMARY_CHARS);
                String description = truncate(normalize(block.description()), MAX_EVIDENCE_DESCRIPTION_CHARS);
                return EvidenceBlock.builder()
                        .sourceType(block.sourceType())
                        .sourceId(block.sourceId())
                        .summary(summary)
                        .description(description)
                        .status(block.status())
                        .timestamp(block.timestamp())
                        .build();
        }

        private String truncate(String value, int maxChars) {
                if (value == null) {
                        return null;
                }
                String v = value.trim();
                if (v.length() <= maxChars) {
                        return v;
                }
                return v.substring(0, maxChars).trim();
        }

        private <T> List<T> safeList(List<T> value) {
                return value == null ? List.of() : value;
        }

        private ValidationFailure validateStrict(List<AiStructuredResponse.RequirementItem> requirements) {
                if (requirements == null || requirements.isEmpty()) {
                        return new ValidationFailure("No requirements", Map.of());
                }

                Set<String> seenIds = new HashSet<>();
                Set<String> seenNormalizedDescriptions = new HashSet<>();

                for (AiStructuredResponse.RequirementItem req : requirements) {
                        if (req == null) {
                                return new ValidationFailure("Invalid requirement: null entry", Map.of());
                        }
                        if (req.getId() == null || req.getId().isBlank()) {
                                return new ValidationFailure("Missing required field: id", Map.of());
                        }
                        if (!seenIds.add(req.getId().trim())) {
                                return new ValidationFailure("Duplicate requirements", Map.of("duplicateId", req.getId().trim()));
                        }
                        if (req.getSourceRefs() == null || req.getSourceRefs().isEmpty()) {
                                return new ValidationFailure("Missing sourceRefs", Map.of("id", req.getId()));
                        }
                        boolean allBlankRefs = req.getSourceRefs().stream().allMatch(v -> v == null || v.isBlank());
                        if (allBlankRefs) {
                                return new ValidationFailure("Missing sourceRefs", Map.of("id", req.getId()));
                        }
                        String description = req.getDescription();
                        if (description == null || description.isBlank()) {
                                return new ValidationFailure("Missing required field: description", Map.of("id", req.getId()));
                        }
                        if (!description.toUpperCase(Locale.ROOT).contains("SHALL")) {
                                return new ValidationFailure("No \"SHALL\" in requirements", Map.of("id", req.getId()));
                        }
                        if (!MEASURABLE_CRITERIA_PATTERN.matcher(description).find()) {
                                return new ValidationFailure("No measurable acceptance criteria", Map.of("id", req.getId()));
                        }
                        String normalizedDescription = normalizeForDedup(description);
                        if (!seenNormalizedDescriptions.add(normalizedDescription)) {
                                return new ValidationFailure("Duplicate requirements", Map.of("duplicateDescription", normalizedDescription));
                        }
                }

                return null;
        }

        private String normalizeForDedup(String value) {
                if (value == null) {
                        return "";
                }
                return value.trim()
                        .replaceAll("\\s+", " ")
                        .toLowerCase(Locale.ROOT);
        }

        private List<AiStructuredResponse.RequirementItem> filterAndSortRequirements(List<AiStructuredResponse.RequirementItem> input) {
                if (input == null || input.isEmpty()) {
                        return List.of();
                }
                List<AiStructuredResponse.RequirementItem> filtered = input.stream()
                        .filter(req -> req != null
                                && req.getId() != null
                                && !req.getId().isBlank()
                                && req.getDescription() != null
                                && !req.getDescription().isBlank()
                                && req.getSourceRefs() != null
                                && !req.getSourceRefs().isEmpty()
                                && req.getSourceRefs().stream().anyMatch(v -> v != null && !v.isBlank()))
                        .toList();

                List<AiStructuredResponse.RequirementItem> sorted = new ArrayList<>(filtered);
                sorted.sort(Comparator.comparing(AiStructuredResponse.RequirementItem::getId, Comparator.nullsLast(String::compareTo)));
                // Deduplicate by id first, then by normalized description, keeping first occurrence deterministically (sorted by id)
                Map<String, AiStructuredResponse.RequirementItem> byId = new java.util.LinkedHashMap<>();
                for (AiStructuredResponse.RequirementItem req : sorted) {
                        byId.putIfAbsent(req.getId().trim(), req);
                }
                List<AiStructuredResponse.RequirementItem> deduped = new ArrayList<>(byId.values());
                Set<String> seenDesc = new HashSet<>();
                List<AiStructuredResponse.RequirementItem> finalList = new ArrayList<>();
                for (AiStructuredResponse.RequirementItem req : deduped) {
                        String descKey = normalizeForDedup(req.getDescription());
                        if (seenDesc.add(descKey)) {
                                finalList.add(req);
                        }
                }
                return List.copyOf(finalList);
        }

        private enum Step {
                INPUT,
                FETCH,
                EVIDENCE_BUILD,
                RAW_BUILD,
                AI_PROCESSING,
                VALIDATION,
                EXPORT,
                PERSIST,
                DONE;

                Step next() {
                        return switch (this) {
                                case INPUT -> FETCH;
                                case FETCH -> EVIDENCE_BUILD;
                                case EVIDENCE_BUILD -> RAW_BUILD;
                                case RAW_BUILD -> AI_PROCESSING;
                                case AI_PROCESSING -> VALIDATION;
                                case VALIDATION -> EXPORT;
                                case EXPORT -> PERSIST;
                                case PERSIST -> DONE;
                                case DONE -> DONE;
                        };
                }
        }

        private static class StepFailedException extends RuntimeException {
                private final Step step;
                private final int attempt;
                private final String reason;

                StepFailedException(Step step, int attempt, String reason, Throwable cause) {
                        super(reason, cause);
                        this.step = step;
                        this.attempt = attempt;
                        this.reason = reason;
                }
        }

        private static class ValidationFailure {
                private final String reason;
                private final Map<String, Object> context;

                ValidationFailure(String reason, Map<String, Object> context) {
                        this.reason = reason;
                        this.context = context == null ? Map.of() : context;
                }
        }

        private static class ReportRunContext {
                private final String projectConfigId;
                private final UUID createdBy;
                private final boolean useAi;
                private final List<String> logs;

                private String exportType;
                private boolean aiStrict;
                private String filePath;

                private final Map<Step, Integer> stepAttempts = new java.util.EnumMap<>(Step.class);
                private final Map<Step, Object> results = new EnumMap<>(Step.class);

                ReportRunContext(String projectConfigId, UUID createdBy, boolean useAi, String exportType, List<String> logs) {
                        this.projectConfigId = projectConfigId;
                        this.createdBy = createdBy;
                        this.useAi = useAi;
                        this.exportType = exportType;
                        this.logs = logs;
                }

                void clearFrom(Step step) {
                        Step cursor = step;
                        while (cursor != Step.DONE) {
                                results.remove(cursor);
                                cursor = cursor.next();
                        }
                }

                <T> T get(Class<T> type, Step step) {
                        Object value = results.get(step);
                        if (type.isInstance(value)) {
                                return type.cast(value);
                        }
                        throw new IllegalStateException("Missing result for step " + step);
                }

                <T> T tryGet(Class<T> type, Step step) {
                        Object value = results.get(step);
                        return type.isInstance(value) ? type.cast(value) : null;
                }

                UUID tryUuidProjectConfigId() {
                        InputResult input = tryGet(InputResult.class, Step.INPUT);
                        return input == null ? null : input.uuidProjectConfigId();
                }

                int tryCountIssues() {
                        FetchResult fetch = tryGet(FetchResult.class, Step.FETCH);
                        return fetch == null ? 0 : fetch.issues().size();
                }

                int tryCountCommits() {
                        FetchResult fetch = tryGet(FetchResult.class, Step.FETCH);
                        return fetch == null ? 0 : fetch.commits().size();
                }

                int tryCountActivities() {
                        FetchResult fetch = tryGet(FetchResult.class, Step.FETCH);
                        return fetch == null ? 0 : fetch.activities().size();
                }

                int tryCountEvidenceBlocks() {
                        EvidenceBuildResult evidence = tryGet(EvidenceBuildResult.class, Step.EVIDENCE_BUILD);
                        return evidence == null ? 0 : evidence.evidenceBlocks().size();
                }

                int tryRawEvidenceBytes() {
                        RawBuildResult raw = tryGet(RawBuildResult.class, Step.RAW_BUILD);
                        return raw == null ? 0 : raw.rawEvidenceBytes();
                }

                int tryCountRequirements() {
                        ValidationResult validation = tryGet(ValidationResult.class, Step.VALIDATION);
                        if (validation != null) {
                                return validation.requirementsCount();
                        }
                        AiProcessingResult ai = tryGet(AiProcessingResult.class, Step.AI_PROCESSING);
                        return ai == null ? 0 : ai.requirementsCount();
                }
        }

        private record InputResult(
                String projectConfigId,
                UUID uuidProjectConfigId,
                UUID createdBy,
                boolean useAi,
                String exportType
        ) {}

        private record FetchResult(
                List<IssueResponse> issues,
                List<GithubCommitResponse> commits,
                List<UnifiedActivityResponse> activities
        ) {}

        private record EvidenceBuildResult(
                List<EvidenceBlock> evidenceBlocks
        ) {}

        private record RawBuildResult(
                String rawEvidenceJson,
                int rawEvidenceBytes
        ) {}

        private record AiProcessingResult(
                boolean aiStrict,
                AiStructuredResponse structured,
                String srsContent,
                int requirementsCount
        ) {}

        private record ValidationResult(
                boolean aiStrict,
                AiStructuredResponse structured,
                String srsContent,
                int requirementsCount
        ) {}

        private record ExportResult(
                String exportType,
                String filePath
        ) {}

        private record PersistGuardResult(
                String filePath
        ) {}

        public ReportMetadataResponse getReport(UUID reportId) {
                return toMetadataResponse(findReport(reportId));
        }

        public PageResponse<ReportMetadataResponse> listReports(String projectConfigId, String type, String status, UUID createdBy, int page, int size) {
                // The current domain only persists completed reports. For UI status cards, treat other statuses as empty.
                if (status != null && !status.isBlank()) {
                        String s = status.trim().toUpperCase(java.util.Locale.ROOT);
                        if (!REPORT_STATUS_COMPLETED.equals(s)) {
                                return PageResponse.<ReportMetadataResponse>builder()
                                        .content(List.of())
                                        .page(page)
                                        .size(size)
                                        .totalElements(0L)
                                        .totalPages(0)
                                        .build();
                        }
                }
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

        public ReportDownload loadReportDownloadForCreatedBy(UUID reportId, UUID createdBy) {
                Report report = findReport(reportId);
                if (createdBy != null && report.getCreatedBy() != null && !report.getCreatedBy().equals(createdBy)) {
                        throw new org.springframework.security.access.AccessDeniedException("Students can only download own reports");
                }
                return loadReportDownload(reportId);
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
                if (lowerCaseName.endsWith(".xlsx")) {
                        return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                }
                return MediaType.APPLICATION_OCTET_STREAM;
        }

        public record ReportDownload(Resource resource, String fileName, MediaType mediaType) {}
}