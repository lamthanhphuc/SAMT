package com.example.user_groupservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * UserGroup entity representing the many-to-many relationship
 * between users and groups, including the group role (LEADER/MEMBER).
 * 
 * Business rules:
 * - Each group can have only ONE LEADER
 * - Each user can only be in ONE group per semester
 * - Supports soft delete via deletedAt field
 */
@Entity
@Table(name = "user_groups")
@IdClass(UserGroupId.class)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGroup {
    
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private GroupRole role;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.role == null) {
            this.role = GroupRole.MEMBER;
        }
    }
    
    /**
     * Perform soft delete on this membership.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }
    
    /**
     * Check if this member is the leader.
     */
    public boolean isLeader() {
        return this.role == GroupRole.LEADER;
    }
    
    /**
     * Demote this member from LEADER to MEMBER.
     */
    public void demoteToMember() {
        this.role = GroupRole.MEMBER;
    }
    
    /**
     * Promote this member to LEADER.
     */
    public void promoteToLeader() {
        this.role = GroupRole.LEADER;
    }
}
