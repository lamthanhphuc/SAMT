package com.example.user_groupservice.controller;

import com.example.user_groupservice.dto.request.AddMemberRequest;
import com.example.user_groupservice.dto.response.MemberResponse;
import com.example.user_groupservice.security.CurrentUser;
import com.example.user_groupservice.service.GroupMemberService;
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
 * Controller for Group Member operations
 * Handles member management: add, remove, promote, demote
 * 
 * Authorization:
 * - POST /api/groups/{groupId}/members: ADMIN or LECTURER
 * - GET /api/groups/{groupId}/members: AUTHENTICATED
 * - PUT /api/groups/{groupId}/members/{userId}/promote: ADMIN or LECTURER
 * - PUT /api/groups/{groupId}/members/{userId}/demote: ADMIN or LECTURER
 * - DELETE /api/groups/{groupId}/members/{userId}: ADMIN
 */
@RestController
@RequestMapping("/api/groups/{groupId}/members")
@RequiredArgsConstructor
@Slf4j
public class GroupMemberController {
    
    private final GroupMemberService memberService;
    
    /**
     * UC24 - Add Member to Group
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('LECTURER')")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable Long groupId,
            @Valid @RequestBody AddMemberRequest request) {
        
        log.info("Adding member {} to group {}", request.getUserId(), groupId);
        MemberResponse response = memberService.addMember(groupId, request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * UC24 - Get All Members of Group
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MemberResponse>> getGroupMembers(@PathVariable Long groupId) {
        log.info("Getting members of group {}", groupId);
        List<MemberResponse> response = memberService.getGroupMembers(groupId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * UC25 - Promote Member to Leader
     */
    @PutMapping("/{userId}/promote")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LECTURER')")
    public ResponseEntity<MemberResponse> promoteToLeader(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        
        log.info("Promoting user {} to LEADER in group {}", userId, groupId);
        MemberResponse response = memberService.promoteToLeader(groupId, userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * UC25 - Demote Leader to Member
     */
    @PutMapping("/{userId}/demote")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LECTURER')")
    public ResponseEntity<MemberResponse> demoteToMember(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        
        log.info("Demoting user {} to MEMBER in group {}", userId, groupId);
        MemberResponse response = memberService.demoteToMember(groupId, userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * UC26 - Remove Member from Group
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @AuthenticationPrincipal CurrentUser currentUser) {

        log.info("Removing member {} from group {}", userId, groupId);
        Long actorId = currentUser.getUserId();
        memberService.removeMember(groupId, userId, actorId);
        return ResponseEntity.noContent().build();
    }
}
