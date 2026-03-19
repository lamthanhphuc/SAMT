package com.example.analysisservice.controller;

import com.example.analysisservice.dto.request.AiRequest;
import com.example.analysisservice.dto.response.AiResponse;
import com.example.analysisservice.dto.response.AiStructuredResponse;
import com.example.analysisservice.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiService aiService;

    @PostMapping("/generate-srs")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER','STUDENT')")
    public AiResponse generate(@Valid @RequestBody AiRequest request) {

        String result =
                aiService.generateSrs(request.getRawRequirements());

        return new AiResponse(result);
    }

    @PostMapping("/generate-srs-structured")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER','STUDENT')")
    public AiStructuredResponse generateStructured(@Valid @RequestBody AiRequest request) {
        return aiService.generateSrsStructured(request.getRawRequirements(), request.isStrict());
    }
}
