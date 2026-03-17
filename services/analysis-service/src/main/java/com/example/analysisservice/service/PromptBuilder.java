package com.example.analysisservice.service;

public class PromptBuilder {

      private PromptBuilder() {
      }

      public static String buildRequirementsExtractionPrompt(String evidenceJson) {

            return """
You are a strict software requirements extractor.

MANDATORY RULES:
1) Use only provided evidence.
2) Do not invent requirements.
3) If information is missing or ambiguous, add OPEN_QUESTION entries.
4) Every requirement MUST include sourceRefs[] with at least one sourceId.
5) Every requirement description MUST contain at least one SHALL statement.
6) Every requirement description MUST include measurable acceptance criteria.

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

Evidence JSON:
""" + evidenceJson;
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
""" + validatedRequirementsJson;
    }
}
