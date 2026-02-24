package com.example.user_groupservice.repository;

import com.example.user_groupservice.entity.GroupRole;
import com.example.user_groupservice.entity.UserSemesterMembership;
import com.example.user_groupservice.entity.UserSemesterMembershipId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UserSemesterMembership entity.
 * Manages user memberships in groups per semester.
 * 
 * IMPORTANT: Use findLeaderByGroupIdWithLock() for promote/demote operations 
 * to prevent race conditions.
 */
@Repository
public interface UserSemesterMembershipRepository 
        extends JpaRepository<UserSemesterMembership, UserSemesterMembershipId> {
    
    /**
     * Find membership by userId and groupId (respects @SQLRestriction - only non-deleted).
     * Used for checking if user is member of a group.
     */
    @Query("SELECT usm FROM UserSemesterMembership usm " +
           "WHERE usm.id.userId = :userId AND usm.groupId = :groupId " +
           "AND usm.deletedAt IS NULL")
    Optional<UserSemesterMembership> findByUserIdAndGroupId(@Param("userId") Long userId, 
                                                             @Param("groupId") Long groupId);
    
    /**
     * Check if user already has a group in this semester.
     * Enforces: One group per student per semester.
     */
    @Query("SELECT CASE WHEN COUNT(usm) > 0 THEN true ELSE false END " +
           "FROM UserSemesterMembership usm " +
           "WHERE usm.id.userId = :userId AND usm.id.semesterId = :semesterId " +
           "AND usm.deletedAt IS NULL")
    boolean existsByUserIdAndSemesterId(@Param("userId") Long userId, 
                                        @Param("semesterId") Long semesterId);
    
    /**
     * Check if a group already has a user with specific role (e.g., LEADER).
     * Enforces: Only one leader per group.
     */
    @Query("SELECT CASE WHEN COUNT(usm) > 0 THEN true ELSE false END " +
           "FROM UserSemesterMembership usm " +
           "WHERE usm.groupId = :groupId AND usm.groupRole = :role " +
           "AND usm.deletedAt IS NULL")
    boolean existsByGroupIdAndRole(@Param("groupId") Long groupId, 
                                   @Param("role") GroupRole role);
    
    /**
     * Find leader with pessimistic lock to prevent race conditions.
     * CRITICAL: Use this for promote/demote role operations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT usm FROM UserSemesterMembership usm " +
           "WHERE usm.groupId = :groupId AND usm.groupRole = 'LEADER' " +
           "AND usm.deletedAt IS NULL")
    Optional<UserSemesterMembership> findLeaderByGroupIdWithLock(@Param("groupId") Long groupId);
    
    /**
     * Count non-leader members in a group.
     * Used to check if leader can be removed (only if no other members exist).
     */
    @Query("SELECT COUNT(usm) FROM UserSemesterMembership usm " +
           "WHERE usm.groupId = :groupId AND usm.groupRole = 'MEMBER' " +
           "AND usm.deletedAt IS NULL")
    long countMembersByGroupId(@Param("groupId") Long groupId);
    
    /**
     * Count members in a group by specific role (excludes soft-deleted).
     * Generic method for counting any role type.
     * 
     * @param groupId the group ID
     * @param groupRole the role to count (MEMBER or LEADER)
     * @return count of active members with specified role
     */
    @Query("SELECT COUNT(usm) FROM UserSemesterMembership usm " +
           "WHERE usm.groupId = :groupId AND usm.groupRole = :groupRole " +
           "AND usm.deletedAt IS NULL")
    long countByGroupIdAndGroupRoleAndDeletedAtIsNull(@Param("groupId") Long groupId, 
                                                       @Param("groupRole") GroupRole groupRole);
    
    /**
     * Count total members in a group (including leader).
     */
    @Query("SELECT COUNT(usm) FROM UserSemesterMembership usm " +
           "WHERE usm.groupId = :groupId AND usm.deletedAt IS NULL")
    long countAllMembersByGroupId(@Param("groupId") Long groupId);
    
    /**
     * Find all memberships for a user (across all semesters).
     */
    @Query("SELECT usm FROM UserSemesterMembership usm " +
           "WHERE usm.id.userId = :userId AND usm.deletedAt IS NULL")
    List<UserSemesterMembership> findAllByUserId(@Param("userId") Long userId);
    
    /**
     * Find all memberships in a specific group (respects soft delete).
     */
    @Query("SELECT usm FROM UserSemesterMembership usm " +
           "WHERE usm.groupId = :groupId AND usm.deletedAt IS NULL")
    List<UserSemesterMembership> findAllByGroupId(@Param("groupId") Long groupId);
    
    /**
     * Find all memberships for a specific user (including soft deleted).
     * Used by Kafka consumer when user is hard deleted.
     */
    @Query("SELECT usm FROM UserSemesterMembership usm WHERE usm.id.userId = :userId")
    List<UserSemesterMembership> findAllByUserIdIncludingDeleted(@Param("userId") Long userId);
    
    /**
     * Find all memberships in a specific semester.
     */
    @Query("SELECT usm FROM UserSemesterMembership usm " +
           "WHERE usm.id.semesterId = :semesterId AND usm.deletedAt IS NULL")
    List<UserSemesterMembership> findAllBySemesterId(@Param("semesterId") Long semesterId);
    
    /**
     * Find membership by composite ID (respects soft delete).
     * CRITICAL: Use this instead of findById() to respect @SQLRestriction.
     * 
     * Why needed:
     * - JPA's findById() uses primary key directly, bypassing @SQLRestriction
     * - This can return soft-deleted records, causing data corruption
     * - This method explicitly checks deleted_at IS NULL
     */
    @Query("SELECT usm FROM UserSemesterMembership usm " +
           "WHERE usm.id = :id AND usm.deletedAt IS NULL")
    Optional<UserSemesterMembership> findByIdAndNotDeleted(@Param("id") UserSemesterMembershipId id);
    
    /**
     * Batch count members (including leader) for multiple groups.
     * Used to prevent N+1 query problem in group listing.
     * 
     * Performance: Replaces N queries with 1 query when listing groups.
     * 
     * @param groupIds list of group IDs to count members for
     * @return list of projections containing groupId and memberCount
     */
    @Query("SELECT usm.groupId as groupId, COUNT(usm) as memberCount " +
           "FROM UserSemesterMembership usm " +
           "WHERE usm.groupId IN :groupIds " +
           "AND usm.deletedAt IS NULL " +
           "GROUP BY usm.groupId")
    List<GroupMemberCount> countMembersByGroupIds(@Param("groupIds") List<Long> groupIds);
    
    /**
     * Projection interface for batch member counting.
     * Used by countMembersByGroupIds() to return group ID and count pairs.
     */
    interface GroupMemberCount {
        Long getGroupId();
        Long getMemberCount();
    }
}
