package com.example.user_groupservice.controller;

import com.example.user_groupservice.dto.request.AddMemberRequest;
import com.example.user_groupservice.dto.request.AssignRoleRequest;
import com.example.user_groupservice.dto.request.CreateGroupRequest;
import com.example.user_groupservice.dto.request.UpdateGroupRequest;
import com.example.user_groupservice.dto.response.*;
import com.example.user_groupservice.entity.GroupRole;
import com.example.user_groupservice.service.GroupMemberService;
import com.example.user_groupservice.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for group operations.
 * 
 * Authorization:
 * - POST /groups: ADMIN only
 * - GET /groups/{groupId}: AUTHENTICATED
 * - GET /groups: AUTHENTICATED
 * - PUT /groups/{groupId}: ADMIN only
 * - DELETE /groups/{groupId}: ADMIN only
 * - POST /groups/{groupId}/members: ADMIN only
 * - PUT /groups/{groupId}/members/{userId}/role: ADMIN only
 * - DELETE /groups/{groupId}/members/{userId}: ADMIN only
 * - GET /groups/{groupId}/members: AUTHENTICATED
 */
@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
@Slf4j
public class GroupController {
    
    private final GroupService groupService;
    private final GroupMemberService memberService;
    
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
    public ResponseEntity<GroupDetailResponse> getGroupById(@PathVariable UUID groupId) {
        
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
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) UUID lecturerId) {
        
        PageResponse<GroupListResponse> response = groupService.listGroups(
                page, size, semester, lecturerId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update Group
     */
    @PutMapping("/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        
        GroupResponse response = groupService.updateGroup(groupId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete Group (soft delete)
     */
    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID groupId) {
        
        groupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }
    
    // ==================== Member Management ====================
    
    /**
     * UC24 - Add Member to Group
     */
    @PostMapping("/{groupId}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable UUID groupId,
            @Valid @RequestBody AddMemberRequest request) {
        
        MemberResponse response = memberService.addMember(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * UC25 - Assign Group Role
     */
    @PutMapping("/{groupId}/members/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MemberResponse> assignRole(
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequest request) {
        
        MemberResponse response = memberService.assignRole(groupId, userId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * UC26 - Remove Member from Group
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        
        memberService.removeMember(groupId, userId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Get Group Members
     */
    @GetMapping("/{groupId}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GroupMembersResponse> getGroupMembers(
            @PathVariable UUID groupId,
            @RequestParam(required = false) GroupRole role) {
        
        GroupMembersResponse response = memberService.getGroupMembers(groupId, role);
        return ResponseEntity.ok(response);
    }
}
