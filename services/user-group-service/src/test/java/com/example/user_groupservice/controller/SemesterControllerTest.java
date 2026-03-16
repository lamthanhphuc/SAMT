package com.example.user_groupservice.controller;

import com.example.user_groupservice.dto.request.CreateSemesterRequest;
import com.example.user_groupservice.dto.response.SemesterResponse;
import com.example.user_groupservice.security.SecurityConfig;
import com.example.user_groupservice.service.SemesterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = SemesterController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
)
@ActiveProfiles("test")
@Import(SemesterControllerTest.TestSecurityConfig.class)
class SemesterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SemesterService semesterService;

    @AfterEach
    void resetMocks() {
        reset(semesterService);
    }

    @Test
    void getSemesterRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/semesters/1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createSemesterRejectsNonAdminUsers() throws Exception {
        mockMvc.perform(post("/api/semesters")
                .with(user("student").roles("STUDENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isForbidden());
    }

    @Test
    void createSemesterValidatesRequestBody() throws Exception {
        CreateSemesterRequest invalidRequest = CreateSemesterRequest.builder()
            .semesterCode("   ")
            .semesterName("Semester A")
            .startDate(LocalDate.of(2026, 1, 1))
            .endDate(LocalDate.of(2026, 5, 1))
            .build();

        mockMvc.perform(post("/api/semesters")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(semesterService, never()).createSemester(any());
    }

    @Test
    void createSemesterReturnsCreatedResponseForAdmins() throws Exception {
        when(semesterService.createSemester(any(CreateSemesterRequest.class))).thenReturn(
            SemesterResponse.builder()
                .id(1L)
                .semesterCode("2026A")
                .semesterName("Semester A")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 5, 1))
                .isActive(false)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build()
        );

        mockMvc.perform(post("/api/semesters")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.semesterCode").value("2026A"));
    }

    @Test
    void activateSemesterIsRestrictedToAdmins() throws Exception {
        mockMvc.perform(patch("/api/semesters/1/activate")
                .with(user("student").roles("STUDENT")))
            .andExpect(status().isForbidden());
    }

    private CreateSemesterRequest validRequest() {
        return CreateSemesterRequest.builder()
            .semesterCode("2026A")
            .semesterName("Semester A")
            .startDate(LocalDate.of(2026, 1, 1))
            .endDate(LocalDate.of(2026, 5, 1))
            .build();
    }

    @TestConfiguration
    @EnableMethodSecurity
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