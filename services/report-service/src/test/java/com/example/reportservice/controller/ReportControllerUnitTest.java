package com.example.reportservice.controller;

import com.example.reportservice.dto.request.ReportRequest;
import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerUnitTest {

    @Mock
    private ReportingService reportingService;

    @InjectMocks
    private ReportController reportController;

    @Test
    void generateSrsShouldReturnCreatedResponse() {
        ReportRequest request = new ReportRequest();
        request.setProjectConfigId(5L);
        request.setUseAi(true);
        request.setExportType("PDF");

        String subject = UUID.randomUUID().toString();
        Jwt jwt = new Jwt("token", null, null, Map.of("alg", "none"), Map.of("sub", subject));

        ReportResponse expected = new ReportResponse(UUID.randomUUID(), "COMPLETED", LocalDateTime.now(), "/api/reports/1/download");
        when(reportingService.generate(5L, subject, true, "PDF")).thenReturn(expected);

        var response = reportController.generateSrs(request, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(reportingService).generate(5L, subject, true, "PDF");
    }
}
