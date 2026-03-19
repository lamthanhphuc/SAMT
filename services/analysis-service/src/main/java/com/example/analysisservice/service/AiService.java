package com.example.analysisservice.service;

import com.example.analysisservice.dto.response.AiStructuredResponse;

public interface AiService {

    String generateSrs(String rawRequirements);

    AiStructuredResponse generateSrsStructured(String rawRequirements, boolean strict);
}