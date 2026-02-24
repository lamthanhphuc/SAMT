package com.example.user_groupservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * User Semester Membership Entity
 * 
 * Business Rules:
 * - PRIMARY KEY: (user_id, semester_id)
 * - UNIQUE: user can only belong to 1 group in 1 semester
 * - UNIQUE: only 1 LEADER per group (enforced by database unique index)
 * - user_id: Logical reference to Identity Service (NO FK CONSTRAINT)
 * - deleted_by: Logical reference to Identity Service (NO FK CONSTRAINT)
 */
@Entity
@Table(name = "user_semester_membership")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSemesterMembership {
    
    @EmbeddedId
    private UserSemesterMembershipId id;  // Composite PK (userId, semesterId)
    
    @Column(name = "group_id", nullable = false)
    private Long groupId;  // FK to groups table
    
    @Enumerated(EnumType.STRING)
    @Column(name = "group_role", nullable = false, length = 20)
    private GroupRole groupRole;  // LEADER or MEMBER
    
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;  // Logical reference to Identity Service (NO FK)
    
    @Version
    @Column(name = "version")
    private Integer version;
    
    @PrePersist
    protected void onCreate() {
        joinedAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * Soft delete the membership
     * @param deletedByUserId User ID who performed the deletion
     */
    public void softDelete(Long deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if membership is soft deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Promote member to LEADER role
     */
    public void promoteToLeader() {
        this.groupRole = GroupRole.LEADER;
        this.updatedAt = Instant.now();
    }

    /**
     * Demote LEADER to MEMBER role
     */
    public void demoteToMember() {
        this.groupRole = GroupRole.MEMBER;
        this.updatedAt = Instant.now();
    }
}
