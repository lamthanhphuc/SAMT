package com.example.user_groupservice.repository;

import com.example.user_groupservice.entity.GroupRole;
import com.example.user_groupservice.entity.UserGroup;
import com.example.user_groupservice.entity.UserGroupId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserGroup entity.
 * Note: @SQLRestriction on UserGroup entity automatically filters soft-deleted records.
 */
@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {
    
    /**
     * Find membership by user ID and group ID.
     */
    @Query("SELECT ug FROM UserGroup ug " +
           "WHERE ug.userId = :userId AND ug.groupId = :groupId")
    Optional<UserGroup> findByUserIdAndGroupId(@Param("userId") UUID userId, 
                                               @Param("groupId") UUID groupId);
    
    /**
     * Check if user is already in a group for the given semester.
     * Joins with Group to check semester.
     */
    @Query("SELECT CASE WHEN COUNT(ug) > 0 THEN true ELSE false END " +
           "FROM UserGroup ug JOIN Group g ON ug.groupId = g.id " +
           "WHERE ug.userId = :userId AND g.semester = :semester")
    boolean existsByUserIdAndSemester(@Param("userId") UUID userId, 
                                      @Param("semester") String semester);
    
    /**
     * Check if user is already a member of the specific group.
     */
    @Query("SELECT CASE WHEN COUNT(ug) > 0 THEN true ELSE false END " +
           "FROM UserGroup ug " +
           "WHERE ug.userId = :userId AND ug.groupId = :groupId")
    boolean existsByUserIdAndGroupId(@Param("userId") UUID userId, 
                                     @Param("groupId") UUID groupId);
    
    /**
     * Check if group already has a leader.
     */
    @Query("SELECT CASE WHEN COUNT(ug) > 0 THEN true ELSE false END " +
           "FROM UserGroup ug " +
           "WHERE ug.groupId = :groupId AND ug.role = :role")
    boolean existsByGroupIdAndRole(@Param("groupId") UUID groupId, 
                                   @Param("role") GroupRole role);
    
    /**
     * Find the current leader of a group (without lock).
     */
    @Query("SELECT ug FROM UserGroup ug " +
           "WHERE ug.groupId = :groupId AND ug.role = 'LEADER'")
    Optional<UserGroup> findLeaderByGroupId(@Param("groupId") UUID groupId);
    
    /**
     * Find the current leader of a group with PESSIMISTIC LOCK.
     * CRITICAL: Use this for assignRole operations to prevent race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ug FROM UserGroup ug " +
           "WHERE ug.groupId = :groupId AND ug.role = 'LEADER'")
    Optional<UserGroup> findLeaderByGroupIdWithLock(@Param("groupId") UUID groupId);
    
    /**
     * Count members (excluding leader) in a group.
     */
    @Query("SELECT COUNT(ug) FROM UserGroup ug " +
           "WHERE ug.groupId = :groupId AND ug.role = 'MEMBER'")
    long countMembersByGroupId(@Param("groupId") UUID groupId);
    
    /**
     * Count all members (including leader) in a group.
     */
    @Query("SELECT COUNT(ug) FROM UserGroup ug " +
           "WHERE ug.groupId = :groupId")
    long countAllMembersByGroupId(@Param("groupId") UUID groupId);
    
    /**
     * Find all members of a group.
     */
    @Query("SELECT ug FROM UserGroup ug " +
           "WHERE ug.groupId = :groupId " +
           "ORDER BY ug.role DESC, ug.createdAt ASC")
    List<UserGroup> findAllByGroupId(@Param("groupId") UUID groupId);
    
    /**
     * Find all members of a group with specific role.
     */
    @Query("SELECT ug FROM UserGroup ug " +
           "WHERE ug.groupId = :groupId AND ug.role = :role")
    List<UserGroup> findAllByGroupIdAndRole(@Param("groupId") UUID groupId, 
                                           @Param("role") GroupRole role);
    
    /**
     * Find all groups that a user belongs to.
     */
    @Query("SELECT ug FROM UserGroup ug " +
           "WHERE ug.userId = :userId " +
           "ORDER BY ug.createdAt DESC")
    List<UserGroup> findAllByUserId(@Param("userId") UUID userId);
    
    /**
     * Find all groups that a user belongs to in a specific semester.
     */
    @Query("SELECT ug FROM UserGroup ug JOIN Group g ON ug.groupId = g.id " +
           "WHERE ug.userId = :userId AND g.semester = :semester")
    List<UserGroup> findAllByUserIdAndSemester(@Param("userId") UUID userId, 
                                                @Param("semester") String semester);
    
    /**
     * Soft delete all memberships in a group (for cascade delete).
     */
    @Query("SELECT ug FROM UserGroup ug WHERE ug.groupId = :groupId")
    List<UserGroup> findByGroupId(@Param("groupId") UUID groupId);
}
