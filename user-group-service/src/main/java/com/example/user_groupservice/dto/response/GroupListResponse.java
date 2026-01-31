package com.example.user_groupservice.dto.response;

import com.example.user_groupservice.entity.Group;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for group list items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupListResponse {
    
    private UUID id;
    private String groupName;
    private String semester;
    private String lecturerName;
    private int memberCount;
    
}
