package com.example.analysisservice.service;

public class PromptBuilder {

      private PromptBuilder() {
      }

      public static String buildRequirementsExtractionPrompt(String evidenceJson, String allowedSourceIdsCsv) {

            return """
You are a strict software requirements extractor.

MANDATORY RULES:
1) Use only provided evidence.
2) Do not invent requirements.
3) If information is missing or ambiguous, add OPEN_QUESTION entries.
4) Every requirement MUST include sourceRefs[] with at least one sourceId from the Allowed sourceIds list.
5) Every requirement description MUST contain at least one SHALL statement.
6) Every requirement description MUST include measurable acceptance criteria (a number + unit, threshold, time, %, etc).
   Examples:
   - "The system SHALL complete X within 5 seconds."
   - "The system SHALL support at least 100 concurrent users."
   - "The system SHALL achieve 99.9% uptime per month."
7) Requirement IDs MUST match EXACTLY one of:
   - FR-XXX (Functional requirement)
   - NFR-XXX (Non-functional requirement)
   where XXX is a 3-digit number (e.g., FR-001).
8) openQuestions MUST be separate from requirements. DO NOT put questions inside requirements.
9) NEVER use ids like "OPEN_QUESTION:002" inside requirements. If it's a question, put it in openQuestions.

OUTPUT RULES (CRITICAL):
- Return ONLY a single JSON object.
- Do NOT include markdown fences, explanations, headings, or any text before/after the JSON.
- Do NOT include trailing commas.
- Use ONLY the keys defined in the schema below.

Return JSON only (no markdown, no comments) with this exact schema:
{
   "requirements": [
      {
         "id": "FR-001",
         "type": "FR" | "NFR",
         "title": "string",
         "description": "string with SHALL and measurable acceptance criteria",
         "sourceRefs": ["sourceId"]
      }
   ],
   "openQuestions": ["OPEN_QUESTION: ..."]
}

Allowed sourceIds (ONLY use values from this list in sourceRefs; one per line):
""" + allowedSourceIdsCsv + """

INVALID EXAMPLES (DO NOT DO THIS):
- requirements[].id: "OPEN_QUESTION:002"  (WRONG: belongs in openQuestions)
- requirements[].sourceRefs: []           (WRONG: MUST be non-empty)
- requirements[].type: "FUNCTIONAL_REQUIREMENT" (WRONG: use "FR")
- requirements[].description missing "SHALL" (WRONG)
- requirements[] containing questions like "What is the ..." (WRONG: put in openQuestions)

Evidence JSON (verbatim):
""" + evidenceJson + "\n";
      }

      public static String buildSrsWriterPrompt(String validatedRequirementsJson) {

        return """
You are a professional Software Requirements Engineer writing a final SRS.

MANDATORY RULES:
1) Use only validated requirements JSON provided below.
2) Do not invent or add new requirements.
3) Keep requirement IDs and sourceRefs as-is.
4) If openQuestions exist, include them in a dedicated section.

Your task is to convert validated requirements into a complete Software Requirements Specification (SRS)
following IEEE 830 / ISO 29148 standard.

The SRS must include:

1. Introduction
   1.1 Purpose
   1.2 Scope
   1.3 Definitions

2. Overall Description
   2.1 Product Perspective
   2.2 Product Functions
   2.3 User Classes
   2.4 Constraints

3. Functional Requirements
   - Each requirement must:
     - Use "SHALL"
     - Have unique ID (FR-XXX)
     - Include Preconditions
     - Include Postconditions
     - Be testable and unambiguous

4. Non-Functional Requirements
   - Performance
   - Security
   - Availability
   - Scalability

Validated Requirements JSON:
""" + validatedRequirementsJson + "\n";
    }
}
