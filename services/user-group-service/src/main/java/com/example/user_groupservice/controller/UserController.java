package com.example.user_groupservice.controller;

import com.example.user_groupservice.dto.request.UpdateUserRequest;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.dto.response.UserGroupsResponse;
import com.example.user_groupservice.dto.response.UserResponse;
import com.example.user_groupservice.security.CurrentUser;
import com.example.user_groupservice.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for user operations.
 * 
 * Authorization:
 * - GET /api/users/{userId}: AUTHENTICATED (ADMIN: all, LECTURER: students, STUDENT: self)
 * - PUT /api/users/{userId}: ADMIN or SELF
 * - GET /api/users: ADMIN only
 * - GET /api/users/{userId}/groups: AUTHENTICATED (same rules as getUserById)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Validated
public class UserController {
    
    private final UserService userService;
    
    /**
     * UC21 - Get User Profile
     */
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable("userId") Long userId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        Long actorId = currentUser.getUserId();
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
            @PathVariable("userId") Long userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        Long actorId = currentUser.getUserId();
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
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be greater than 0") @Max(value = 100, message = "size must be less than or equal to 100") int size,
            @RequestParam(required = false) String status,
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
            @PathVariable("userId") Long userId,
            @RequestParam(required = false) String semester,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        Long actorId = currentUser.getUserId();
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
