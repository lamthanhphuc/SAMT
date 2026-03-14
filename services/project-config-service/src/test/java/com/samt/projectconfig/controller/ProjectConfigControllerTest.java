package com.samt.projectconfig.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samt.projectconfig.dto.request.CreateConfigRequest;
import com.samt.projectconfig.dto.response.ConfigResponse;
import com.samt.projectconfig.exception.GlobalExceptionHandler;
import com.samt.projectconfig.service.ProjectConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectConfigController.class)
@Import({ProjectConfigControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class ProjectConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectConfigService projectConfigService;

    @Test
    void createConfigRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/project-configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createConfigReturnsWrappedApiResponse() throws Exception {
        ConfigResponse response = ConfigResponse.builder()
            .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .groupId(7L)
            .jiraHostUrl("https://acme.atlassian.net")
            .jiraEmail("jira@example.com")
            .jiraApiToken("***abcd")
            .githubRepoUrl("https://github.com/acme/repo")
            .githubToken("ghp_***abcd")
            .state("VALID")
            .createdAt(Instant.parse("2026-03-11T10:00:00Z"))
            .updatedAt(Instant.parse("2026-03-11T10:00:00Z"))
            .build();
        when(projectConfigService.createConfig(any(CreateConfigRequest.class), eq(42L), eq(List.of("ROLE_ADMIN"))))
            .thenReturn(CompletableFuture.completedFuture(response));

        MvcResult result = mockMvc.perform(post("/api/project-configs")
                .with(authentication(adminAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.groupId").value(7))
            .andExpect(jsonPath("$.data.state").value("VALID"))
            .andExpect(jsonPath("$.path").value("/api/project-configs"));
    }

    @Test
    void createConfigReturnsValidationProblemForInvalidPayload() throws Exception {
        CreateConfigRequest invalidRequest = new CreateConfigRequest(
            7L,
            "https://acme.atlassian.net",
            "jira@example.com",
            "short-token",
            "https://github.com/acme/repo",
            "ghp_" + "a".repeat(36)
        );

        mockMvc.perform(post("/api/project-configs")
                .with(authentication(adminAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"));

        verify(projectConfigService, never()).createConfig(any(), any(), any());
    }

    @Test
    void restoreConfigRejectsNonAdminUsers() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/project-configs/admin/{id}/restore", UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .with(authentication(studentAuthentication())))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Access denied"));
    }

    private CreateConfigRequest validRequest() {
        return new CreateConfigRequest(
            7L,
            "https://acme.atlassian.net",
            "jira@example.com",
            "ATATT" + "a".repeat(95),
            "https://github.com/acme/repo",
            "ghp_" + "a".repeat(36)
        );
    }

    private JwtAuthenticationToken adminAuthentication() {
        return authenticationFor("42", "ADMIN");
    }

    private JwtAuthenticationToken studentAuthentication() {
        return authenticationFor("42", "STUDENT");
    }

    private JwtAuthenticationToken authenticationFor(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject)
            .claim("roles", List.of(role))
            .build();
        return new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_" + role));
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
        }
    }
}