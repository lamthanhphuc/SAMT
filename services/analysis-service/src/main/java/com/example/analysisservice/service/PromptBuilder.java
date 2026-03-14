package com.example.analysisservice.service;

public class PromptBuilder {

    public static String buildSrsPrompt(String rawRequirements) {

        return """
You are a professional Software Requirements Engineer.

Your task is to convert raw Jira issues into a complete Software Requirements Specification (SRS)
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

Raw Jira Issues:
""" + rawRequirements;
    }
}
