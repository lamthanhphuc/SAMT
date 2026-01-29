package com.example.user_groupservice.entity;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for UserGroup entity.
 * Consists of userId and groupId.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserGroupId implements Serializable {
    
    private UUID user;
    
    private UUID group;
}
