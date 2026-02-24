package com.example.user_groupservice.controller;

import com.example.user_groupservice.dto.request.*;
import com.example.user_groupservice.dto.response.*;
import com.example.user_groupservice.entity.GroupRole;
import com.example.user_groupservice.security.CurrentUser;
import com.example.user_groupservice.service.GroupMemberService;
import com.example.user_groupservice.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<GroupDetailResponse> getGroupById(@PathVariable Long groupId) {
        
        GroupDetailResponse response = groupService.getGroupById(groupId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * List Groups
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<GroupListResponse>> listGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long lecturerId) {
        
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
            @PathVariable Long groupId,
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
            @PathVariable Long groupId,
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
            @PathVariable Long groupId,
            @Valid @RequestBody UpdateLecturerRequest request) {
        
        GroupResponse response = groupService.updateGroupLecturer(groupId, request);
        return ResponseEntity.ok(response);
    }
}
