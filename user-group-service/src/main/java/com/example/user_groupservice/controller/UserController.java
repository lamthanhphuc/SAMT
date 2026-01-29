package com.example.user_groupservice.controller;

import com.example.user_groupservice.dto.request.UpdateUserRequest;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.dto.response.UserGroupsResponse;
import com.example.user_groupservice.dto.response.UserResponse;
import com.example.user_groupservice.entity.UserStatus;
import com.example.user_groupservice.security.CurrentUser;
import com.example.user_groupservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for user operations.
 * 
 * Authorization:
 * - GET /users/{userId}: AUTHENTICATED (ADMIN: all, LECTURER: students, STUDENT: self)
 * - PUT /users/{userId}: ADMIN or SELF
 * - GET /users: ADMIN only
 * - GET /users/{userId}/groups: AUTHENTICATED (same rules as getUserById)
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    /**
     * UC21 - Get User Profile
     */
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        UUID actorId = currentUser.getUserId();
        List<String> actorRoles = extractRoles(currentUser);
        
        UserResponse response = userService.getUserById(userId, actorId, actorRoles);
        return ResponseEntity.ok(response);
    }
    
    /**
     * UC22 - Update User Profile
     */
    @PutMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        UUID actorId = currentUser.getUserId();
        List<String> actorRoles = extractRoles(currentUser);
        
        UserResponse response = userService.updateUser(userId, request, actorId, actorRoles);
        return ResponseEntity.ok(response);
    }
    
    /**
     * List all users (ADMIN only)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String role) {
        
        PageResponse<UserResponse> response = userService.listUsers(page, size, status, role);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get user's groups
     */
    @GetMapping("/{userId}/groups")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserGroupsResponse> getUserGroups(
            @PathVariable UUID userId,
            @RequestParam(required = false) String semester,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        UUID actorId = currentUser.getUserId();
        List<String> actorRoles = extractRoles(currentUser);
        
        UserGroupsResponse response = userService.getUserGroups(
                userId, semester, actorId, actorRoles);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Extract role names from CurrentUser.
     */
    private List<String> extractRoles(CurrentUser currentUser) {
        return currentUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toList());
    }
}
