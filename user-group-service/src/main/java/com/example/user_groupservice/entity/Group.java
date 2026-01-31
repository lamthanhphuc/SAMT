package com.example.user_groupservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * Group entity representing student groups.
 * Each group belongs to a semester and has one lecturer (UUID reference to Identity Service).
 * NO FK constraint - lecturer validation via gRPC.
 * Supports soft delete via deletedAt field.
 */
@Entity
@Table(name = "groups", 
       uniqueConstraints = @UniqueConstraint(
           name = "uq_group_semester", 
           columnNames = {"group_name", "semester"}
       ))
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    
    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName;
    
    @Column(name = "semester", nullable = false, length = 20)
    private String semester;
    
    @Column(name = "lecturer_id", nullable = false)
    private Long lecturerId;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
    
    /**
     * Perform soft delete on this group.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
