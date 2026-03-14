package com.example.identityservice.repository;

import com.example.identityservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity.
 * 
 * Note: @SQLRestriction on User entity automatically filters deleted_at IS NULL
 * for all standard queries. Use native queries to access soft-deleted users.
 * 
 * @see docs/Identity-Service-Package-Structure.md
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email (for login).
     * Used in UC-LOGIN to validate credentials.
     * Note: Automatically excludes soft-deleted users via @SQLRestriction
     *
     * @param email user email
     * @return Optional<User>
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if email already exists (for registration).
     * Used in UC-REGISTER to check email uniqueness.
     * Note: Automatically excludes soft-deleted users via @SQLRestriction
     *
     * @param email user email
     * @return true if email exists
     */
    boolean existsByEmail(String email);

    // ==================== gRPC Service queries ====================

    /**
     * Find user by ID (excluding deleted).
     * Use case: gRPC GetUser service - return user data to other microservices.
     * Note: Uses @SQLRestriction automatically.
     */
    default Optional<User> findByIdIgnoreDeleted(Long id) {
        return findById(id);
    }

    /**
     * Find all users by IDs (excluding deleted).
     * Use case: gRPC GetUsers service - batch fetch users.
     */
    @Query("SELECT u FROM User u WHERE u.id IN :ids")
    List<User> findAllByIdInIgnoreDeleted(@Param("ids") List<Long> ids);

    // ==================== Admin queries (include soft-deleted) ====================

    /**
     * Find user by ID including soft-deleted.
     * Use case: Admin restoring a deleted user.
     */
    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<User> findByIdIncludingDeleted(@Param("id") Long id);

    /**
     * Find user by email including soft-deleted.
     * Use case: Check if email was previously registered.
     */
    @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
    Optional<User> findByEmailIncludingDeleted(@Param("email") String email);

    /**
     * Check if email exists including soft-deleted.
     * Use case: Prevent re-registration with previously used email.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE email = :email", nativeQuery = true)
    boolean existsByEmailIncludingDeleted(@Param("email") String email);

    // ==================== External Account Mapping ====================

    /**
     * Find user by Jira account ID.
     * Use case: Sync Service mapping Jira assignee to SAMT user.
     */
    Optional<User> findByJiraAccountId(String jiraAccountId);

    /**
     * Find user by GitHub username.
     * Use case: Sync Service mapping GitHub commit author to SAMT user.
     */
    Optional<User> findByGithubUsername(String githubUsername);

    /**
     * Check if Jira account ID is already mapped to another user.
     * Use case: Admin mapping validation - prevent duplicate mappings.
     */
    boolean existsByJiraAccountId(String jiraAccountId);

    /**
     * Check if GitHub username is already mapped to another user.
     * Use case: Admin mapping validation - prevent duplicate mappings.
     */
    boolean existsByGithubUsername(String githubUsername);

    // ==================== Pagination and Filtering ====================

    /**
     * Find all users with optional status and role filtering (paginated).
     * Use case: gRPC ListUsers service - get paginated user list with filters.
     */
    @Query("SELECT u FROM User u WHERE " +
            "(:status IS NULL OR u.status = :status) AND " +
            "(:role IS NULL OR u.role = :role)")
    Page<User> findAllWithFilters(
            @Param("status") User.Status status,
            @Param("role") User.Role role,
            Pageable pageable);
}
