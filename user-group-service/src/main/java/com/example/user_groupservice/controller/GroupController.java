package com.example.user_groupservice.controller;

import com.example.user_groupservice.dto.request.*;
import com.example.user_groupservice.dto.response.*;
import com.example.user_groupservice.security.CurrentUser;
import com.example.user_groupservice.service.GroupService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for group operations.
 * 
 * Authorization:
 * - POST /groups: ADMIN only
 * - GET /groups/{groupId}: AUTHENTICATED
 * - GET /groups: AUTHENTICATED
 * - PUT /groups/{groupId}: ADMIN only
 * - DELETE /groups/{groupId}: ADMIN only
 * - PATCH /groups/{groupId}/lecturer: ADMIN only
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Slf4j
@Validated
public class GroupController {
    
    private final GroupService groupService;
    
    // ==================== Group CRUD ====================
    
    /**
     * UC23 - Create Group
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        
        GroupResponse response = groupService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get Group Details
     */
    @GetMapping("/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GroupDetailResponse> getGroupById(@PathVariable @Positive(message = "groupId must be greater than 0") Long groupId) {
        
        GroupDetailResponse response = groupService.getGroupById(groupId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * List Groups
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<GroupListResponse>> listGroups(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be greater than 0") @Max(value = 100, message = "size must be less than or equal to 100") int size,
            @RequestParam(required = false) @Positive(message = "semesterId must be greater than 0") Long semesterId,
            @RequestParam(required = false) @Positive(message = "lecturerId must be greater than 0") Long lecturerId) {
        
        PageResponse<GroupListResponse> response = groupService.listGroups(
                page, size, semesterId, lecturerId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update Group
     */
    @PutMapping("/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable @Positive(message = "groupId must be greater than 0") Long groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        
        GroupResponse response = groupService.updateGroup(groupId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete Group (soft delete)
     */
    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable @Positive(message = "groupId must be greater than 0") Long groupId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        Long actorId = currentUser.getUserId();
        groupService.deleteGroup(groupId, actorId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * UC27 - Update Group Lecturer
     */
    @PatchMapping("/{groupId}/lecturer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupResponse> updateGroupLecturer(
            @PathVariable @Positive(message = "groupId must be greater than 0") Long groupId,
            @Valid @RequestBody UpdateLecturerRequest request) {
        
        GroupResponse response = groupService.updateGroupLecturer(groupId, request);
        return ResponseEntity.ok(response);
    }
}
