package com.example.analysisservice.controller;

import com.example.analysisservice.dto.request.AiRequest;
import com.example.analysisservice.service.AiService;
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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiAnalysisController.class)
@Import(com.example.analysisservice.config.SecurityConfig.class)
@SuppressWarnings({"deprecation", "removal"})
class AiAnalysisControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiService aiService;

    @Test
    void shouldReturnUnauthorizedWithoutToken() throws Exception {
        AiRequest request = new AiRequest();
        request.setRawRequirements("FR-001");

        mockMvc.perform(post("/internal/ai/generate-srs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAdminRole() throws Exception {
        AiRequest request = new AiRequest();
        request.setRawRequirements("FR-001");

        Mockito.when(aiService.generateSrs(Mockito.anyString())).thenReturn("SRS");

        mockMvc.perform(post("/internal/ai/generate-srs")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt
                                .subject(UUID.randomUUID().toString())
                                .claim("service", "api-gateway")
                                .claim("jti", UUID.randomUUID().toString())
                                .claim("roles", List.of("ADMIN"))
                                .claim("iss", "samt-gateway"))
                            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
