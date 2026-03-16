package com.example.identityservice.controller;

import com.example.identityservice.dto.LoginRequest;
import com.example.identityservice.dto.LoginResponse;
import com.example.identityservice.dto.LogoutRequest;
import com.example.identityservice.dto.RefreshTokenRequest;
import com.example.identityservice.dto.RegisterRequest;
import com.example.identityservice.dto.RegisterResponse;
import com.example.identityservice.dto.UserDto;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.AccountLockedException;
import com.example.identityservice.exception.EmailAlreadyExistsException;
import com.example.identityservice.exception.GlobalExceptionHandler;
import com.example.identityservice.exception.InvalidCredentialsException;
import com.example.identityservice.security.SecurityContextHelper;
import com.example.identityservice.service.AuditService;
import com.example.identityservice.service.AuthService;
import com.example.identityservice.service.RefreshTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = AuthController.class,
    excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.example.identityservice.security.JwtAuthenticationFilter.class)
)
@Import({AuthControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
@SuppressWarnings({"deprecation", "removal"})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @Test
    void registerReturnsCreatedResponse() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "student.new@samt.local",
            "Str0ng@Pass!",
            "Str0ng@Pass!",
            "Student New",
            "STUDENT"
        );
        User user = new User();
        user.setId(7L);
        user.setEmail("student.new@samt.local");
        user.setFullName("Student New");
        user.setRole(User.Role.STUDENT);
        user.setStatus(User.Status.ACTIVE);
        user.setJiraAccountId("jira-acc-id");
        user.setGithubUsername("student-gh");
        setCreatedAt(user);
        UserDto userDto = UserDto.fromEntity(user);
        when(authService.register(any(RegisterRequest.class))).thenReturn(RegisterResponse.of(userDto, "access-token", "refresh-token"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("access-token"))
            .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void registerReturnsBadRequestForInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    void registerMapsEmailExistsToConflict() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "student.exists@samt.local",
            "Str0ng@Pass!",
            "Str0ng@Pass!",
            "Student Exists",
            "STUDENT"
        );
        when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyExistsException());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsOkResponse() throws Exception {
        LoginRequest request = new LoginRequest("student@samt.local", "Str0ng@Pass!");
        when(authService.login(any(LoginRequest.class))).thenReturn(LoginResponse.of("access-token", "refresh-token", 900));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("access-token"))
            .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void loginReturnsBadRequestForMissingPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@samt.local\"}"))
            .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    void loginMapsInvalidCredentialsToUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest("student@samt.local", "wrong-password");
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginMapsLockedAccountToForbidden() throws Exception {
        LoginRequest request = new LoginRequest("student@samt.local", "Str0ng@Pass!");
        when(authService.login(any(LoginRequest.class))).thenThrow(new AccountLockedException());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void refreshReturnsOkResponse() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
        when(refreshTokenService.refreshToken("refresh-token")).thenReturn(LoginResponse.of("access-new", "refresh-new", 900));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("access-new"));
    }

    @Test
    void refreshReturnsBadRequestForBlankToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"\"}"))
            .andExpect(status().isBadRequest());

        verify(refreshTokenService, never()).refreshToken(any(String.class));
    }

    @Test
    void logoutRevokesTokenAndWritesAudit() throws Exception {
        when(securityContextHelper.getCurrentUserId()).thenReturn(Optional.of(42L));
        when(securityContextHelper.getCurrentUserEmail()).thenReturn(Optional.of("student@samt.local"));

        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LogoutRequest("refresh-token"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        verify(refreshTokenService).revokeToken("refresh-token");
        verify(auditService).logLogout(42L, "student@samt.local");
    }

    @Test
    void logoutReturnsBadRequestForBlankToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"\"}"))
            .andExpect(status().isBadRequest());

        verify(refreshTokenService, never()).revokeToken(any(String.class));
        verify(auditService, never()).logLogout(any(), any());
    }

    private void setCreatedAt(User user) {
        try {
            java.lang.reflect.Field createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(user, LocalDateTime.of(2026, 3, 14, 10, 0, 0));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to prepare user", ex);
        }
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
        }
    }
}
