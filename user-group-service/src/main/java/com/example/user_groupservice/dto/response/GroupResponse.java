package com.example.user_groupservice.dto.response;

import com.example.user_groupservice.entity.Group;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for group creation/update operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    
    private UUID id;
    private String groupName;
    private String semester;
    private UUID lecturerId;
    private String lecturerName;
    
    /**
     * Create GroupResponse from Group entity.
     */
    public static GroupResponse from(Group group) {
        return GroupResponse.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .semester(group.getSemester())
                .lecturerId(group.getLecturer().getId())
                .lecturerName(group.getLecturer().getFullName())
                .build();
    }
}
