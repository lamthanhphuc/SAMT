package com.example.user_groupservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite Primary Key for UserSemesterMembership
 * Enforces business rule: One user can only belong to ONE group per semester
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSemesterMembershipId implements Serializable {
    
    /**
     * Logical reference to Identity Service users.id (NO FK)
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "semester_id", nullable = false)
    private Long semesterId;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSemesterMembershipId)) return false;
        UserSemesterMembershipId that = (UserSemesterMembershipId) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(semesterId, that.semesterId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, semesterId);
    }
}
