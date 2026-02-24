package com.example.user_groupservice.dto.response;

import com.example.user_groupservice.entity.Group;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for group list items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupListResponse {
    
    private Long id;
    private String groupName;
    private Long semesterId;
    private String semesterCode;
    private String lecturerName;
    private int memberCount;
    
}
