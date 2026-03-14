package com.example.user_groupservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for group creation/update operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    
    private Long id;
    private String groupName;
    private Long semesterId;
    private String semesterCode;
    private Long lecturerId;
    private String lecturerName;
    
}
