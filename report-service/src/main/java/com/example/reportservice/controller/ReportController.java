package com.example.reportservice.controller;
import com.example.reportservice.dto.request.ReportRequest;
import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.service.ReportingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportingService service;

    @PostMapping("/srs")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ReportResponse generateSrs(
            @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());

        return service.generate(
                request.getProjectConfigId(),
                userId,
                request.isUseAi(),
                request.getExportType());
    }
}
