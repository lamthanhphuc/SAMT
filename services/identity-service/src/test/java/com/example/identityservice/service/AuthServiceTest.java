package com.example.identityservice.service;

import com.example.identityservice.dto.LoginRequest;
import com.example.identityservice.dto.RegisterRequest;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.AccountLockedException;
import com.example.identityservice.exception.EmailAlreadyExistsException;
import com.example.identityservice.exception.InvalidCredentialsException;
import com.example.identityservice.exception.PasswordMismatchException;
import com.example.identityservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User activeUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest(
            "student@example.com",
            "Password@123",
            "Password@123",
            "Student Example",
            "STUDENT"
        );
        loginRequest = new LoginRequest("student@example.com", "Password@123");
        activeUser = new User();
        activeUser.setId(42L);
        activeUser.setEmail("student@example.com");
        activeUser.setFullName("Student Example");
        activeUser.setPasswordHash("hashed-password");
        activeUser.setRole(User.Role.STUDENT);
        activeUser.setStatus(User.Status.ACTIVE);
    }

    @Test
    void registerRejectsPasswordMismatchBeforeEncodingOrSaving() {
        RegisterRequest invalidRequest = new RegisterRequest(
            registerRequest.email(),
            registerRequest.password(),
            "different-password",
            registerRequest.fullName(),
            registerRequest.role()
        );

        assertThatThrownBy(() -> authService.register(invalidRequest))
            .isInstanceOf(PasswordMismatchException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerMapsUniqueConstraintViolationsToEmailAlreadyExists() {
        when(passwordEncoder.encode(registerRequest.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.register(registerRequest))
            .isInstanceOf(EmailAlreadyExistsException.class);

        verify(auditService, never()).logUserCreated(any());
    }

    @Test
    void loginRejectsInvalidPasswordBeforeIssuingTokens() {
        when(userRepository.findByEmail(loginRequest.email())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(loginRequest.password(), activeUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
            .isInstanceOf(InvalidCredentialsException.class);

        verify(auditService).logLoginFailure(loginRequest.email(), "Invalid password");
        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void loginChecksPasswordBeforeLockedStatus() {
        activeUser.setStatus(User.Status.LOCKED);
        when(userRepository.findByEmail(loginRequest.email())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(loginRequest.password(), activeUser.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest))
            .isInstanceOf(AccountLockedException.class);

        verify(auditService).logLoginDenied(activeUser, "Account is locked");
        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }
}