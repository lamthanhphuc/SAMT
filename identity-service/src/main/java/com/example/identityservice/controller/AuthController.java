package com.example.identityservice.controller;

import com.example.common.api.ApiResponseFactory;
import com.example.identityservice.dto.*;
import com.example.identityservice.security.SecurityContextHelper;
import com.example.identityservice.service.AuditService;
import com.example.identityservice.service.AuthService;
import com.example.identityservice.service.RefreshTokenService;
import com.example.identityservice.web.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller.
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
    @Operation(summary = "Register user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad request",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "409", description = "Email already exists",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<com.example.common.api.ApiResponse<RegisterResponse>> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest servletRequest
    ) {
        RegisterResponse response = authService.register(request);
        return success(HttpStatus.CREATED, response, servletRequest);
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
    @Operation(summary = "Login user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login succeeded",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<com.example.common.api.ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest
    ) {
        LoginResponse response = authService.login(request);
        return success(HttpStatus.OK, response, servletRequest);
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
    @Operation(summary = "Refresh access token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Refresh succeeded",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<com.example.common.api.ApiResponse<LoginResponse>> refresh(
        @Valid @RequestBody RefreshTokenRequest request,
        HttpServletRequest servletRequest
    ) {
        LoginResponse response = refreshTokenService.refreshToken(request.refreshToken());
        return success(HttpStatus.OK, response, servletRequest);
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
     * - Silent failure: if token not found/already revoked, still return 200
     * 
     * @param request LogoutRequest with refreshToken
     * @return 200 OK
     */
    @Operation(summary = "Logout user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logout succeeded",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<com.example.common.api.ApiResponse<Void>> logout(
        @Valid @RequestBody LogoutRequest request,
        HttpServletRequest servletRequest
    ) {
        // Get current user for audit
        Long userId = securityContextHelper.getCurrentUserId().orElse(null);
        String userEmail = securityContextHelper.getCurrentUserEmail().orElse(null);
        
        // Revoke refresh token (silent failure if not found)
        refreshTokenService.revokeToken(request.refreshToken());
        
        // Audit logout
        auditService.logLogout(userId, userEmail);
        
        return success(HttpStatus.OK, null, servletRequest);
    }

    private <T> ResponseEntity<com.example.common.api.ApiResponse<T>> success(HttpStatus status, T data, HttpServletRequest request) {
        return ResponseEntity.status(status).body(
            ApiResponseFactory.success(
                status.value(),
                data,
                request.getRequestURI(),
                resolveCorrelationId(request)
            )
        );
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        }
        return correlationId;
    }
}
