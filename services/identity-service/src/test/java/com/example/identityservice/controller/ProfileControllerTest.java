package com.example.identityservice.controller;

import com.example.identityservice.dto.UpdateProfileRequest;
import com.example.identityservice.entity.User;
import com.example.identityservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.FilterType;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = ProfileController.class,
    excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.example.identityservice.security.JwtAuthenticationFilter.class)
)
@Import(ProfileControllerTest.TestSecurityConfig.class)
@SuppressWarnings({"deprecation", "removal"})
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void shouldReturnUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnCurrentProfileForAuthenticatedUser() throws Exception {
        User user = buildUser(42L, "student@example.com", "Nguyen Van A");
        Mockito.when(userService.getCurrentUserProfile(42L)).thenReturn(user);

        mockMvc.perform(get("/profile")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authentication(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.email").value("student@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.path").value("/profile"));
    }

    @Test
    void shouldUpdateCurrentProfileForAuthenticatedUser() throws Exception {
        User updatedUser = buildUser(42L, "updated@example.com", "Nguyen Van Updated");
        UpdateProfileRequest request = new UpdateProfileRequest("updated@example.com", "Nguyen Van Updated");

        Mockito.when(userService.updateCurrentUserProfile(eq(42L), any(UpdateProfileRequest.class)))
                .thenReturn(updatedUser);

        mockMvc.perform(put("/profile")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authentication(buildUser(42L, "student@example.com", "Nguyen Van A"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.email").value("updated@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van Updated"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.path").value("/profile"));
    }

    private UsernamePasswordAuthenticationToken authentication(User user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    private User buildUser(Long id, String email, String fullName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(User.Role.STUDENT);
        user.setStatus(User.Status.ACTIVE);
        user.setPasswordHash("hashed-password");
        user.setGithubUsername("student-gh");
        user.setJiraAccountId("jira-account-1234567890");
        return setCreatedAt(user);
    }

    private User setCreatedAt(User user) {
        try {
            java.lang.reflect.Field createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(user, LocalDateTime.of(2026, 3, 11, 10, 0, 0));
            return user;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to prepare test user", ex);
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
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .build();
        }
    }
}