package com.example.user_groupservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * Group entity representing student groups.
 * Each group belongs to a semester and has one lecturer (Long reference to Identity Service).
 * NO FK constraint across services - lecturer validation via gRPC.
 * Supports soft delete via deletedAt field.
 * 
 * CRITICAL:
 * - ID is Long (BIGINT), NOT UUID
 * - semesterId is Long (FK to semesters table), NOT String
 * - lecturerId is Long (logical reference to Identity Service)
 * - deletedBy is Long (logical reference to Identity Service)
 */
@Entity
@Table(name = "groups")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    
    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;
    
    @Column(name = "semester_id", nullable = false)
    private Long semesterId;  // FK to semesters table
    
    @Column(name = "lecturer_id", nullable = false)
    private Long lecturerId;  // Logical reference to Identity Service (NO FK)
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
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
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * Perform soft delete on this group.
     * @param deletedByUserId User ID who performed the deletion
     */
    public void softDelete(Long deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }
}
