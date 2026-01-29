package com.example.user_groupservice.repository;

import com.example.user_groupservice.entity.User;
import com.example.user_groupservice.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity.
 * Note: @SQLRestriction on User entity automatically filters soft-deleted records.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Find user by ID (soft-deleted users are automatically filtered).
     */
    Optional<User> findById(UUID id);
    
    /**
     * Find user by email (soft-deleted users are automatically filtered).
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Check if email already exists.
     */
    boolean existsByEmail(String email);
    
    /**
     * Find all active users with pagination.
     */
    Page<User> findAll(Pageable pageable);
    
    /**
     * Find users by status with pagination.
     */
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    
    /**
     * Find users by multiple IDs.
     */
    List<User> findAllByIdIn(List<UUID> ids);
}
