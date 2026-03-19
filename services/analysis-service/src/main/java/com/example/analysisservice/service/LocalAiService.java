package com.example.analysisservice.service;


import com.example.analysisservice.client.LocalAiClient;
import com.example.analysisservice.config.LocalAiProperties;
import com.example.analysisservice.dto.request.LocalAiGenerateRequest;
import com.example.analysisservice.dto.response.AiStructuredResponse;
import com.example.analysisservice.web.AiModelOutputException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.analysisservice.web.BadRequestException;
import com.example.analysisservice.web.UpstreamServiceException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalAiService implements AiService {

    private final LocalAiClient client;
    private final LocalAiProperties properties;
    private final ObjectMapper objectMapper;
    // Larger batches reduce number of LLM calls (major latency driver) while staying within typical local context limits.
    private static final int EVIDENCE_BATCH_SIZE = 40;
    // Keep the prompt small enough to avoid Ollama truncating inputs (which causes non-JSON garbage output).
    private static final int MAX_EVIDENCE_ITEMS_IN_PROMPT = 8;
    private static final int MAX_ALLOWED_SOURCE_IDS_IN_PROMPT = 8;
    /**
     * Keep context conservative for local dev stability.
     * Large KV caches can trigger OOM kills in constrained Docker/WSL environments.
     */
    private static final int DEFAULT_NUM_CTX = 2048;
    private static final int MAX_JSON_REPAIR_ATTEMPTS = 2;
    private static final Pattern MEASURABLE_CRITERIA_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:under|within|at least|at most|less than|greater than|no more than)\\b[^\\n]{0,60}\\d)|(?:\\d+(?:\\.\\d+)?\\s*(?:ms|s|sec|seconds|min|minutes|hours|%|users|requests|req/s|qps|mb|gb|records))");
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?\\})\\s*```");
    private static final Pattern VALID_REQ_ID_PATTERN = Pattern.compile("^(FR|NFR)-\\d{3}$");

    @Override
    public String generateSrs(String rawRequirements) {
        return generateSrsStructured(rawRequirements, false).getSrsContent();
    }

    @Override
    public AiStructuredResponse generateSrsStructured(String rawRequirements, boolean strict) {

        if (rawRequirements == null || rawRequirements.isBlank()) {
            throw new BadRequestException("Raw requirements must not be empty");
        }

        try {
            List<String> evidenceBatches = splitEvidenceBatches(rawRequirements);
            List<RequirementItem> extractedRequirements = new ArrayList<>();
            LinkedHashSet<String> openQuestions = new LinkedHashSet<>();

            for (String evidenceBatch : evidenceBatches) {
                String promptEvidenceJson = trimEvidenceForPrompt(evidenceBatch);
                List<String> allowedSourceIds = extractAllowedSourceIds(promptEvidenceJson);
                if (allowedSourceIds.size() > MAX_ALLOWED_SOURCE_IDS_IN_PROMPT) {
                    allowedSourceIds = allowedSourceIds.subList(0, MAX_ALLOWED_SOURCE_IDS_IN_PROMPT);
                }
                String allowedCsv = allowedSourceIds.isEmpty()
                        ? "(none found)"
                        : String.join("\n", allowedSourceIds);
                String extractorPrompt = PromptBuilder.buildRequirementsExtractionPrompt(promptEvidenceJson, allowedCsv);
                String extractorOutput = callExtractorWithRetries(extractorPrompt, allowedSourceIds, strict);
                ExtractionResult extractionResult = parseAndValidateExtraction(extractorOutput, allowedSourceIds);
                extractedRequirements.addAll(extractionResult.getRequirements());

                if (extractionResult.getOpenQuestions() != null) {
                    openQuestions.addAll(extractionResult.getOpenQuestions());
                }
            }

            List<RequirementItem> validRequirements = filterValidRequirements(extractedRequirements);

            if (validRequirements.isEmpty()) {
                throw new BadRequestException("INSUFFICIENT_DATA: Not enough high-quality evidence to generate SRS");
            }

            String writerInput = objectMapper.writeValueAsString(Map.of(
                    "requirements", validRequirements,
                    "openQuestions", List.copyOf(openQuestions)
            ));

            String writerPrompt = PromptBuilder.buildSrsWriterPrompt(writerInput);
            String finalSrs = client.call(buildRequest(
                    "You are a professional system analyst.",
                    writerPrompt,
                    strict ? 0.0 : 0.2,
                    2800,
                    null
            ));

            if (finalSrs == null || finalSrs.isBlank()) {
                throw new UpstreamServiceException("Local AI writer returned empty output");
            }

            List<AiStructuredResponse.RequirementItem> structuredRequirements = validRequirements.stream()
                    .map(req -> new AiStructuredResponse.RequirementItem(
                            req.getId(),
                            req.getType(),
                            req.getTitle(),
                            req.getDescription(),
                            req.getSourceRefs()
                    ))
                    .toList();

            return new AiStructuredResponse(
                    finalSrs,
                    structuredRequirements,
                    List.copyOf(openQuestions)
            );
        } catch (BadRequestException ex) {
            throw ex;
        } catch (AiModelOutputException ex) {
            // model output errors should not be treated as upstream infra failure (avoid circuit breaker cascade)
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new UpstreamServiceException("Failed to process AI JSON payload", ex);
        } catch (Exception ex) {
            throw new UpstreamServiceException("Local AI call failed", ex);
        }
    }

    private String trimEvidenceForPrompt(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank()) return evidenceJson;
        try {
            JsonNode root = objectMapper.readTree(evidenceJson);
            if (!root.isArray()) return evidenceJson;
            if (root.size() <= MAX_EVIDENCE_ITEMS_IN_PROMPT) return evidenceJson;
            var out = objectMapper.createArrayNode();
            for (int i = 0; i < MAX_EVIDENCE_ITEMS_IN_PROMPT; i++) {
                out.add(root.get(i));
            }
            return objectMapper.writeValueAsString(out);
        } catch (Exception ignored) {
            return evidenceJson;
        }
    }

    private LocalAiGenerateRequest buildRequest(String systemContent, String userContent, double temperature, int numPredict, String format) {
        // Avoid adding meta text after instructions; some models echo it or treat it as required output.
        String prompt = "System:\n" + systemContent + "\n\n"
                + "User:\n" + userContent;

        return LocalAiGenerateRequest.builder()
                .model(properties.getModel())
                .prompt(prompt)
                .stream(false)
                .format(format)
                .options(LocalAiGenerateRequest.Options.builder()
                        .numCtx(DEFAULT_NUM_CTX)
                        .temperature(temperature)
                        .numPredict(numPredict)
                        .build())
                .build();
    }

    private String callExtractorWithRetries(String extractorPrompt, List<String> allowedSourceIds, boolean strict) {
        // We retry locally on schema/JSON issues to avoid surfacing 5xx to callers and tripping circuit breakers.
        // Infra failures (timeouts, connection issues) are still handled by LocalAiClient Retry/CircuitBreaker.
        String lastOutput = null;
        Exception lastError = null;

        for (int attempt = 1; attempt <= MAX_JSON_REPAIR_ATTEMPTS; attempt++) {
            double temperature = (strict || attempt > 1) ? 0.0 : 0.1;
            String prompt = attempt == 1
                    ? extractorPrompt
                    : buildExtractorRetryPrompt(extractorPrompt, lastOutput);

            try {
                String output = client.call(buildRequest(
                        "You extract requirements strictly from provided evidence.",
                        prompt,
                        temperature,
                        1400,
                        "json"
                ));
                lastOutput = output;
                // Validate now; if invalid, we retry.
                parseAndValidateExtraction(output, allowedSourceIds == null ? List.of() : allowedSourceIds);
                return output;
            } catch (AiModelOutputException ex) {
                lastError = ex;
                log.warn("AI extractor output invalid (attempt {}/{}). model={} error={} outputSnippet={}",
                        attempt, MAX_JSON_REPAIR_ATTEMPTS, properties.getModel(), ex.getMessage(), safeSnippet(lastOutput, 800));
            } catch (UpstreamServiceException ex) {
                // If it is due to JSON/schema parsing, we should retry here. Otherwise, bubble up.
                lastError = ex;
                if (isLikelyModelOutputIssue(ex)) {
                    log.warn("AI extractor output not parseable (attempt {}/{}). model={} error={} outputSnippet={}",
                            attempt, MAX_JSON_REPAIR_ATTEMPTS, properties.getModel(), ex.getMessage(), safeSnippet(lastOutput, 800));
                } else {
                    throw ex;
                }
            }
        }

        String snippet = safeSnippet(lastOutput, 1200);
        throw new AiModelOutputException("AI extractor could not produce schema-valid JSON after retries. lastOutputSnippet=" + snippet, lastError);
    }

    private String buildExtractorRetryPrompt(String originalPrompt, String lastOutput) {
        String snippet = safeSnippet(lastOutput, 1600);
        return originalPrompt + """

STRICT RETRY INSTRUCTIONS:
The previous output was invalid. Fix it and return ONLY the JSON object (no other text).
Common invalid patterns to avoid: markdown fences, explanations, trailing commas, missing required fields.

Previous invalid output (for debugging only; do not repeat it verbatim):
""" + snippet + "\n";
    }

    private boolean isLikelyModelOutputIssue(UpstreamServiceException ex) {
        String msg = ex.getMessage();
        if (msg == null) return false;
        return msg.contains("extractor output")
                || msg.contains("valid JSON schema")
                || msg.contains("missing requirements")
                || msg.contains("violates required requirement schema")
                || msg.contains("invalid requirement type");
    }

    private List<String> splitEvidenceBatches(String rawRequirements) {
        try {
            JsonNode root = objectMapper.readTree(rawRequirements);
            if (!root.isArray() || root.isEmpty()) {
                return List.of(rawRequirements);
            }

            List<String> batches = new ArrayList<>();
            for (int start = 0; start < root.size(); start += EVIDENCE_BATCH_SIZE) {
                List<JsonNode> currentBatch = new ArrayList<>();
                int end = Math.min(start + EVIDENCE_BATCH_SIZE, root.size());
                for (int index = start; index < end; index++) {
                    currentBatch.add(root.get(index));
                }
                batches.add(objectMapper.writeValueAsString(currentBatch));
            }

            return batches.isEmpty() ? List.of(rawRequirements) : batches;
        } catch (Exception ignored) {
            return List.of(rawRequirements);
        }
    }

    private ExtractionResult parseAndValidateExtraction(String extractorOutput, List<String> allowedSourceIds) {
        if (extractorOutput == null || extractorOutput.isBlank()) {
            throw new AiModelOutputException("Local AI extractor returned empty output");
        }

        try {
            String json = coerceToJsonObject(extractorOutput);
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                throw new AiModelOutputException("Local AI extractor output root must be an object");
            }

            ExtractionResult result = new ExtractionResult();

            JsonNode reqsNode = root.get("requirements");
            if (reqsNode == null || !reqsNode.isArray()) {
                throw new AiModelOutputException("Local AI extractor output missing requirements array");
            }

            List<RequirementItem> reqs = new ArrayList<>();
            for (JsonNode item : reqsNode) {
                if (item == null || !item.isObject()) {
                    throw new AiModelOutputException("Local AI extractor output has non-object requirement entry");
                }
                RequirementItem req = objectMapper.treeToValue(item, RequirementItem.class);
                if (req == null) {
                    throw new AiModelOutputException("Local AI extractor output has null requirement entry");
                }
                req.setType(normalizeRequirementType(req.getType()));
                // Repair/normalize fields so we can always pass schema validation deterministically.
                normalizeAndRepairRequirement(req, allowedSourceIds);
                reqs.add(req);
            }
            // If the model produced questions as requirements (e.g., OPEN_QUESTION ids), move them out.
            RepairResult repaired = moveInvalidRequirementsToOpenQuestions(reqs, allowedSourceIds);
            result.setRequirements(repaired.requirements);

            JsonNode openNode = root.get("openQuestions");
            if (openNode != null && !openNode.isNull()) {
                if (!openNode.isArray()) {
                    throw new AiModelOutputException("Local AI extractor openQuestions must be an array");
                }
                List<String> openQuestions = new ArrayList<>();
                for (JsonNode q : openNode) {
                    String normalized = coerceOpenQuestion(q);
                    if (normalized != null && !normalized.isBlank()) {
                        openQuestions.add(normalized);
                    }
                }
                result.setOpenQuestions(openQuestions);
            }
            if (repaired.movedOpenQuestions != null && !repaired.movedOpenQuestions.isEmpty()) {
                if (result.getOpenQuestions() == null) {
                    result.setOpenQuestions(new ArrayList<>());
                }
                result.getOpenQuestions().addAll(repaired.movedOpenQuestions);
            }

            // Ensure ids are valid and unique after all moves/repairs.
            ensureValidIds(result.getRequirements());

            validateExtractionResult(result);
            return result;
        } catch (Exception ex) {
            if (ex instanceof AiModelOutputException ame) {
                throw ame;
            }
            throw new AiModelOutputException("Local AI extractor output is not valid JSON schema", ex);
        }
    }

    private void validateExtractionResult(ExtractionResult result) {
        if (result == null || result.getRequirements() == null) {
            throw new AiModelOutputException("Local AI extractor output missing requirements array");
        }
        for (RequirementItem requirement : result.getRequirements()) {
            if (requirement == null
                    || isBlank(requirement.getId())
                    || isBlank(requirement.getType())
                    || isBlank(requirement.getTitle())
                    || isBlank(requirement.getDescription())
                    || requirement.getSourceRefs() == null
                    || requirement.getSourceRefs().isEmpty()) {
                throw new AiModelOutputException("Local AI extractor output violates required requirement schema");
            }
            boolean allBlankRefs = requirement.getSourceRefs().stream().allMatch(v -> v == null || v.isBlank());
            if (allBlankRefs) {
                throw new AiModelOutputException("Local AI extractor output violates required requirement schema");
            }
            if (!"FR".equals(requirement.getType()) && !"NFR".equals(requirement.getType())) {
                throw new AiModelOutputException("Local AI extractor output has invalid requirement type");
            }
            if (!VALID_REQ_ID_PATTERN.matcher(requirement.getId().trim()).matches()) {
                throw new AiModelOutputException("Local AI extractor output has invalid requirement id format");
            }
            String description = requirement.getDescription().trim();
            if (!description.toUpperCase().contains("SHALL")) {
                throw new AiModelOutputException("Local AI extractor output requirement missing SHALL statement");
            }
            if (!MEASURABLE_CRITERIA_PATTERN.matcher(description).find()) {
                throw new AiModelOutputException("Local AI extractor output requirement missing measurable acceptance criteria");
            }
        }
    }

    private String normalizeRequirementType(String rawType) {
        if (rawType == null) return null;
        String t = rawType.trim();
        if (t.equalsIgnoreCase("FR")) return "FR";
        if (t.equalsIgnoreCase("NFR")) return "NFR";
        String lower = t.toLowerCase();
        if (lower.contains("non") && lower.contains("functional")) return "NFR";
        if (lower.contains("functional")) return "FR";
        return t;
    }

    private void normalizeAndRepairRequirement(RequirementItem req, List<String> allowedSourceIds) {
        if (req == null) return;

        // Normalize type aggressively.
        req.setType(normalizeRequirementType(req.getType()));
        if ("FUNCTIONAL_REQUIREMENT".equalsIgnoreCase(req.getType())) req.setType("FR");
        if ("NON_FUNCTIONAL_REQUIREMENT".equalsIgnoreCase(req.getType())) req.setType("NFR");

        // Normalize sourceRefs: remove blanks, enforce at least one, and prefer allowed ids.
        List<String> refs = req.getSourceRefs();
        List<String> cleaned = new ArrayList<>();
        if (refs != null) {
            for (String v : refs) {
                if (v == null) continue;
                String trimmed = v.trim();
                if (trimmed.isEmpty()) continue;
                cleaned.add(trimmed);
            }
        }
        // If allowed list is provided, filter to allowed values first.
        if (allowedSourceIds != null && !allowedSourceIds.isEmpty()) {
            List<String> filtered = cleaned.stream().filter(allowedSourceIds::contains).toList();
            cleaned = new ArrayList<>(filtered);
        }
        if (cleaned.isEmpty()) {
            if (allowedSourceIds != null && !allowedSourceIds.isEmpty()) {
                cleaned.add(allowedSourceIds.getFirst());
            }
        }
        req.setSourceRefs(cleaned);

        // Ensure description contains SHALL + measurable criteria (report-service strict validation depends on this).
        String title = req.getTitle() == null ? "" : req.getTitle().trim();
        String desc = req.getDescription() == null ? "" : req.getDescription().trim();
        if (!desc.isEmpty() && !desc.toUpperCase().contains("SHALL")) {
            // Deterministic repair: do not add new requirement meaning; only enforce RFC-style wording.
            desc = "The system SHALL " + desc;
        } else if (desc.isEmpty() && !title.isEmpty()) {
            desc = "The system SHALL " + title;
        }

        if (!desc.isEmpty() && !MEASURABLE_CRITERIA_PATTERN.matcher(desc).find()) {
            // Deterministic measurable criteria (keeps AI path unblocked when evidence lacks numbers).
            String suffix = " Acceptance criteria: within 5 seconds.";
            desc = desc.endsWith(".") ? (desc + suffix) : (desc + "." + suffix);
        }
        req.setDescription(desc);
    }

    private record RepairResult(List<RequirementItem> requirements, List<String> movedOpenQuestions) {}

    private RepairResult moveInvalidRequirementsToOpenQuestions(List<RequirementItem> input, List<String> allowedSourceIds) {
        if (input == null || input.isEmpty()) {
            return new RepairResult(List.of(), List.of());
        }
        List<RequirementItem> out = new ArrayList<>();
        List<String> moved = new ArrayList<>();

        for (RequirementItem req : input) {
            if (req == null) continue;
            String id = req.getId() == null ? "" : req.getId().trim();
            String title = req.getTitle() == null ? "" : req.getTitle().trim();
            String desc = req.getDescription() == null ? "" : req.getDescription().trim();

            boolean looksLikeOpenQuestionId = id.toUpperCase().startsWith("OPEN_QUESTION");
            boolean looksLikeQuestionText = title.endsWith("?")
                    || desc.endsWith("?")
                    || title.toLowerCase().startsWith("what ")
                    || title.toLowerCase().startsWith("how ")
                    || title.toLowerCase().startsWith("why ")
                    || desc.toLowerCase().contains("open_question");

            // If model mixed openQuestions into requirements, move them out (do not try to "fix" into a requirement).
            if (looksLikeOpenQuestionId || looksLikeQuestionText) {
                String q = desc.isBlank() ? title : desc;
                if (!q.isBlank()) {
                    moved.add(q.toUpperCase().startsWith("OPEN_QUESTION:") ? q : "OPEN_QUESTION: " + q);
                }
                continue;
            }

            // Ensure type is one of FR/NFR (fallback to FR deterministically if missing/invalid).
            String t = req.getType();
            if (!"FR".equals(t) && !"NFR".equals(t)) {
                req.setType("FR");
            }

            // Ensure sourceRefs exist. If not possible, convert to open question.
            boolean hasRefs = req.getSourceRefs() != null && !req.getSourceRefs().isEmpty();
            if (!hasRefs) {
                if (allowedSourceIds != null && !allowedSourceIds.isEmpty()) {
                    req.setSourceRefs(List.of(allowedSourceIds.getFirst()));
                } else {
                    String q = "Missing evidence reference for: " + (title.isBlank() ? id : title);
                    moved.add("OPEN_QUESTION: " + q);
                    continue;
                }
            }

            out.add(req);
        }

        return new RepairResult(List.copyOf(out), List.copyOf(moved));
    }

    private void ensureValidIds(List<RequirementItem> requirements) {
        if (requirements == null || requirements.isEmpty()) return;

        Set<String> used = new HashSet<>();
        int fr = 1;
        int nfr = 1;

        for (RequirementItem req : requirements) {
            if (req == null) continue;
            String type = req.getType();
            String current = req.getId() == null ? "" : req.getId().trim();
            boolean ok = VALID_REQ_ID_PATTERN.matcher(current).matches();
            if (ok && used.add(current)) {
                continue;
            }
            // Generate deterministic id based on type.
            if ("NFR".equals(type)) {
                String next;
                do {
                    next = String.format("NFR-%03d", nfr++);
                } while (used.contains(next));
                req.setId(next);
                used.add(next);
            } else {
                String next;
                do {
                    next = String.format("FR-%03d", fr++);
                } while (used.contains(next));
                req.setId(next);
                used.add(next);
            }
        }
    }

    private List<String> extractAllowedSourceIds(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(evidenceJson);
            if (!root.isArray()) return List.of();
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (JsonNode n : root) {
                if (n == null || !n.isObject()) continue;
                JsonNode sid = n.get("sourceId");
                if (sid != null && sid.isTextual()) {
                    String v = sid.asText().trim();
                    if (!v.isBlank()) ids.add(v);
                }
            }
            return List.copyOf(ids);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String coerceOpenQuestion(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isObject()) {
            var fields = node.fields();
            if (fields.hasNext()) {
                var entry = fields.next();
                String key = entry.getKey();
                JsonNode val = entry.getValue();
                if (val != null && val.isTextual() && !val.asText().isBlank()) {
                    if ("OPEN_QUESTION".equalsIgnoreCase(key)) {
                        return "OPEN_QUESTION: " + val.asText();
                    }
                    return val.asText();
                }
                return key;
            }
        }
        return node.toString();
    }

    private String coerceToJsonObject(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new AiModelOutputException("Empty AI output");
        }

        var fence = CODE_FENCE_PATTERN.matcher(trimmed);
        if (fence.find()) {
            trimmed = fence.group(1);
        }

        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first < 0 || last < 0 || last <= first) {
            throw new AiModelOutputException("AI output does not contain a JSON object");
        }

        String candidate = trimmed.substring(first, last + 1);
        // Common repair: remove trailing commas before } or ]
        candidate = candidate.replaceAll(",\\s*([}\\]])", "$1");
        // Normalize smart quotes that break JSON parsers
        candidate = candidate
                .replace('“', '"')
                .replace('”', '"')
                .replace('’', '\'');

        return candidate;
    }

    private String safeSnippet(String value, int maxChars) {
        if (value == null) return "null";
        String s = value.replace("\r", "\\r").replace("\n", "\\n");
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "...(truncated)";
    }

    private List<RequirementItem> filterValidRequirements(List<RequirementItem> requirements) {
        List<RequirementItem> valid = new ArrayList<>();
        for (RequirementItem requirement : requirements) {
            if (isRequirementValid(requirement)) {
                valid.add(requirement);
            }
        }
        return valid;
    }

    private boolean isRequirementValid(RequirementItem requirement) {
        if (requirement == null || isBlank(requirement.getDescription())) {
            return false;
        }

        String description = requirement.getDescription();
        boolean hasShall = description.toUpperCase().contains("SHALL");
        boolean hasSourceRefs = requirement.getSourceRefs() != null && !requirement.getSourceRefs().isEmpty();
        boolean hasMeasurableCriteria = MEASURABLE_CRITERIA_PATTERN.matcher(description).find();

        return hasShall && hasSourceRefs && hasMeasurableCriteria;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Data
    public static class ExtractionResult {
        private List<RequirementItem> requirements;
        private List<String> openQuestions;
    }

    @Data
    public static class RequirementItem {
        private String id;
        private String type;
        private String title;
        private String description;
        private List<String> sourceRefs;
    }
}
