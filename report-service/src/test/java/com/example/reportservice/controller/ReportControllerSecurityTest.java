package com.example.reportservice.controller;

import com.example.reportservice.dto.request.ReportRequest;
import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.service.ReportingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportController.class)
@Import(com.example.reportservice.config.SecurityConfig.class)
@SuppressWarnings({"deprecation", "removal"})
class ReportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReportingService reportingService;

    @Test
    void shouldReturnUnauthorizedWithoutToken() throws Exception {
        ReportRequest request = new ReportRequest();
        request.setProjectConfigId(1L);
        request.setUseAi(false);
        request.setExportType("PDF");

        mockMvc.perform(post("/api/reports/srs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAuthorizedRole() throws Exception {
        ReportRequest request = new ReportRequest();
        request.setProjectConfigId(1L);
        request.setUseAi(false);
        request.setExportType("PDF");

        Mockito.when(reportingService.generate(Mockito.anyLong(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyString()))
                .thenReturn(new ReportResponse(UUID.randomUUID(), "COMPLETED", java.time.LocalDateTime.now(), "/api/reports/test/download"));

        mockMvc.perform(post("/api/reports/srs")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt
                                .subject(UUID.randomUUID().toString())
                                .claim("service", "api-gateway")
                                .claim("jti", UUID.randomUUID().toString())
                                .claim("roles", java.util.List.of("ADMIN"))
                                        .claim("iss", "samt-gateway"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
