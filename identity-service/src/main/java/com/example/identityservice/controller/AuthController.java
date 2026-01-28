package com.example.identityservice.controller;

import com.example.identityservice.dto.*;
import com.example.identityservice.security.SecurityContextHelper;
import com.example.identityservice.service.AuditService;
import com.example.identityservice.service.AuthService;
import com.example.identityservice.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller.
 * @see docs/SRS.md - API Endpoints Summary
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;

    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            SecurityContextHelper securityContextHelper) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * UC-REGISTER: User Registration
     * 
     * POST /api/auth/register
     * 
     * @param request RegisterRequest with email, password, confirmPassword, fullName, role
     * @return 201 Created with RegisterResponse (user info + tokens)
     * @throws PasswordMismatchException 400 Bad Request (passwords don't match)
     * @throws EmailAlreadyExistsException 409 Conflict (email already registered)
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * UC-LOGIN: User Login
     * 
     * POST /api/auth/login
     * 
     * @param request LoginRequest with email and password
     * @return 200 OK with LoginResponse (tokens)
     * @throws InvalidCredentialsException 401 Unauthorized
     * @throws AccountLockedException 403 Forbidden
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * UC-REFRESH-TOKEN: Refresh Access Token
     * 
     * POST /api/auth/refresh
     * 
     * @param request RefreshTokenRequest with refreshToken
     * @return 200 OK with LoginResponse (new tokens)
     * @throws TokenInvalidException 401 Unauthorized
     * @throws TokenExpiredException 401 Unauthorized
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = refreshTokenService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * UC-LOGOUT: User Logout
     * 
     * POST /api/auth/logout
     * 
     * Requires valid access token in Authorization header.
     * Revokes the provided refresh token.
     * 
     * Design decisions (from SRS):
     * - Idempotent: calling multiple times has same effect
     * - Silent failure: if token not found/already revoked, still return 204
     * 
     * @param request LogoutRequest with refreshToken
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        // Get current user for audit
        Long userId = securityContextHelper.getCurrentUserId().orElse(null);
        String userEmail = securityContextHelper.getCurrentUserEmail().orElse(null);
        
        // Revoke refresh token (silent failure if not found)
        refreshTokenService.revokeToken(request.refreshToken());
        
        // Audit logout
        auditService.logLogout(userId, userEmail);
        
        return ResponseEntity.noContent().build();
    }
}
