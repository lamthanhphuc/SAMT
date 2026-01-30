package com.example.identityservice.service;

import com.example.identityservice.dto.LoginResponse;
import com.example.identityservice.entity.RefreshToken;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.AccountLockedException;
import com.example.identityservice.exception.TokenExpiredException;
import com.example.identityservice.exception.TokenInvalidException;
import com.example.identityservice.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refresh Token Service with rotation and reuse detection.
 * @see docs/Authentication-Authorization-Design.md - Refresh Token Rotation Flow
 * @see docs/SRS.md - UC-REFRESH-TOKEN
 * 
 * Refresh Token TTL: 7 days
 * Format: Opaque random UUID string (NOT a JWT)
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final long refreshTokenExpiration;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            AuditService auditService,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.refreshTokenExpiration = refreshTokenExpiration; // Default: 604800000ms = 7 days
    }

    /**
     * Create new refresh token for user.
     * Used in UC-LOGIN step 5.
     *
     * @param user Authenticated user
     * @return UUID refresh token string
     */
    @Transactional
    public String createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);

        return refreshToken.getToken();
    }

    /**
     * UC-REFRESH-TOKEN: Rotate refresh token and issue new access token.
     * 
     * CRITICAL SECURITY: Check account status BEFORE generating new tokens.
     * 
     * Steps (from IMPLEMENTATION_GUIDE.md):
     * 1. Find token in database
     * 2. Check if token is revoked (REUSE DETECTION)
     * 3. Check if token is expired
     * 4. Get user from token
     * 5. Check user account status (LOCKED) - CRITICAL
     * 6. Revoke old token
     * 7. Generate new refresh token
     * 8. Generate new access token
     * 9. Return new tokens
     *
     * @param tokenString Refresh token UUID string
     * @return LoginResponse with new tokens
     * @throws TokenInvalidException if token not found (401)
     * @throws TokenInvalidException + revoke all if REUSE DETECTED (401)
     * @throws TokenExpiredException if token expired (401)
     * @throws AccountLockedException if user account is locked (403)
     */
    @Transactional
    public LoginResponse refreshToken(String tokenString) {
        // Step 1: Find token in database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenString)
                .orElseThrow(TokenInvalidException::new);

        // Step 2: REUSE DETECTION - Check if token is already revoked
        if (refreshToken.isRevoked()) {
            // SECURITY: Revoked token reused → Token theft detected!
            // Revoke ALL refresh tokens for this user
            User user = refreshToken.getUser();
            log.warn("SECURITY: Refresh token reuse detected for user {}. Revoking all tokens.", user.getId());
            refreshTokenRepository.revokeAllByUser(user);
            
            // Audit: Refresh token reuse (SECURITY EVENT)
            auditService.logRefreshReuse(user);
            
            throw new TokenInvalidException("Token invalid");
        }

        // Step 3: Check if token is expired
        if (refreshToken.isExpired()) {
            throw new TokenExpiredException();
        }

        // Step 4: Get user from token
        User user = refreshToken.getUser();

        // Step 5: CRITICAL - Check account status BEFORE generating new tokens
        if (user.getStatus() != User.Status.ACTIVE) {
            // If account is locked, revoke ALL tokens to prevent bypass
            log.warn("SECURITY: Locked user {} tried to refresh token. Revoking all tokens.", user.getId());
            refreshTokenRepository.revokeAllByUser(user);
            
            // Audit: Refresh denied - account locked
            auditService.logLoginDenied(user, "Account is locked");
            
            throw new AccountLockedException();
        }

        // Step 6: Revoke old token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // Step 7: Generate new refresh token
        String newRefreshToken = createRefreshToken(user);

        // Step 8: Generate new access token
        String newAccessToken = jwtService.generateAccessToken(user);

        // Audit: Refresh success
        auditService.logRefreshSuccess(user);

        // Step 9: Return new tokens (expiresIn = 900 seconds = 15 minutes)
        return LoginResponse.of(newAccessToken, newRefreshToken, 900);
    }

    /**
     * Revoke a specific refresh token.
     * Used in UC-LOGOUT.
     *
     * @param tokenString Refresh token UUID string
     */
    @Transactional
    public void revokeToken(String tokenString) {
        refreshTokenRepository.findByToken(tokenString)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    /**
     * Revoke all refresh tokens for a user.
     * Used for security events (password change, account compromise).
     *
     * @param user User entity
     */
    @Transactional
    public void revokeAllTokens(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }
}
