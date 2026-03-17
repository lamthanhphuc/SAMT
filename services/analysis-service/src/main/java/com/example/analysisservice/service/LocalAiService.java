package com.example.analysisservice.service;


import com.example.analysisservice.client.LocalAiClient;
import com.example.analysisservice.config.LocalAiProperties;
import com.example.analysisservice.dto.request.LocalAiGenerateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.analysisservice.web.BadRequestException;
import com.example.analysisservice.web.UpstreamServiceException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LocalAiService implements AiService {

    private final LocalAiClient client;
    private final LocalAiProperties properties;
    private final ObjectMapper objectMapper;
    private static final int EVIDENCE_BATCH_SIZE = 20;
    private static final int DEFAULT_NUM_CTX = 2048;
    private static final Pattern MEASURABLE_CRITERIA_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:under|within|at least|at most|less than|greater than|no more than)\\b[^\\n]{0,60}\\d)|(?:\\d+(?:\\.\\d+)?\\s*(?:ms|s|sec|seconds|min|minutes|hours|%|users|requests|req/s|qps|mb|gb|records))");

    @Override
    public String generateSrs(String rawRequirements) {

        if (rawRequirements == null || rawRequirements.isBlank()) {
            throw new BadRequestException("Raw requirements must not be empty");
        }

        try {
            List<String> evidenceBatches = splitEvidenceBatches(rawRequirements);
            List<RequirementItem> extractedRequirements = new ArrayList<>();
            LinkedHashSet<String> openQuestions = new LinkedHashSet<>();

            for (String evidenceBatch : evidenceBatches) {
                String extractorPrompt = PromptBuilder.buildRequirementsExtractionPrompt(evidenceBatch);
                String extractorOutput = client.call(buildRequest(
                        "You extract requirements strictly from provided evidence.",
                        extractorPrompt,
                        0.1
                ));

                ExtractionResult extractionResult = parseAndValidateExtraction(extractorOutput);
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
                    0.2
            ));

            if (finalSrs == null || finalSrs.isBlank()) {
                throw new UpstreamServiceException("Local AI writer returned empty output");
            }

            return finalSrs;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new UpstreamServiceException("Failed to process AI JSON payload", ex);
        } catch (Exception ex) {
            throw new UpstreamServiceException("Local AI call failed", ex);
        }
    }

    private LocalAiGenerateRequest buildRequest(String systemContent, String userContent, double temperature) {
        String prompt = "System:\n" + systemContent + "\n\n"
                + "User:\n" + userContent + "\n\n"
                + "Temperature hint: " + temperature;

        return LocalAiGenerateRequest.builder()
                .model(properties.getModel())
                .prompt(prompt)
                .stream(false)
                .options(LocalAiGenerateRequest.Options.builder()
                        .numCtx(DEFAULT_NUM_CTX)
                        .build())
                .build();
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

    private ExtractionResult parseAndValidateExtraction(String extractorOutput) {
        if (extractorOutput == null || extractorOutput.isBlank()) {
            throw new UpstreamServiceException("Local AI extractor returned empty output");
        }

        ExtractionResult result;
        try {
            result = objectMapper.readValue(extractorOutput, ExtractionResult.class);
        } catch (Exception ex) {
            throw new UpstreamServiceException("Local AI extractor output is not valid JSON schema", ex);
        }

        if (result.getRequirements() == null) {
            throw new UpstreamServiceException("Local AI extractor output missing requirements array");
        }

        for (RequirementItem requirement : result.getRequirements()) {
            if (requirement == null
                    || isBlank(requirement.getId())
                    || isBlank(requirement.getType())
                    || isBlank(requirement.getTitle())
                    || isBlank(requirement.getDescription())
                    || requirement.getSourceRefs() == null
                    || requirement.getSourceRefs().isEmpty()) {
                throw new UpstreamServiceException("Local AI extractor output violates required requirement schema");
            }

            if (!"FR".equals(requirement.getType()) && !"NFR".equals(requirement.getType())) {
                throw new UpstreamServiceException("Local AI extractor output has invalid requirement type");
            }
        }

        return result;
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
