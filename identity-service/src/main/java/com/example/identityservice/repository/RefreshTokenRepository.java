package com.example.identityservice.repository;

import com.example.identityservice.entity.RefreshToken;
import com.example.identityservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for RefreshToken entity.
 * @see docs/Identity-Service-Package-Structure.md
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Find refresh token by token string.
     * Used in UC-REFRESH-TOKEN to validate token.
     *
     * @param token UUID token string
     * @return Optional<RefreshToken>
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Revoke all refresh tokens for a user.
     * Used for REUSE DETECTION security feature.
     * When a revoked token is reused, revoke ALL tokens of that user.
     *
     * @param user User entity
     * @return number of updated rows
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
    int revokeAllByUser(@Param("user") User user);

    /**
     * Delete all refresh tokens for a user.
     * Used when user account is deleted (CASCADE handled by DB, but useful for manual cleanup).
     *
     * @param user User entity
     */
    void deleteAllByUser(User user);
}
