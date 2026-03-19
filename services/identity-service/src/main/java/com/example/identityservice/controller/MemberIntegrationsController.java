package com.example.identityservice.controller;

import com.example.identityservice.dto.MemberIntegrationsResponse;
import com.example.identityservice.dto.MemberIntegrationsUpdateRequest;
import com.example.identityservice.entity.User;
import com.example.identityservice.service.JiraIntegrationService;
import com.example.identityservice.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Slf4j
public class MemberIntegrationsController {

    private final UserService userService;
    private final JiraIntegrationService jiraIntegrationService;

    /**
     * API 0: Get member integration info (stored in DB).
     *
     * GET /api/members/{memberId}/integrations
     */
    @GetMapping("/api/members/{memberId}/integrations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MemberIntegrationsResponse> getMemberIntegrations(@PathVariable("memberId") Long memberId) {
        assertCanUpdateMemberIntegrations(memberId);
        User user = userService.getCurrentUserProfile(memberId);
        return ResponseEntity.status(HttpStatus.OK).body(MemberIntegrationsResponse.fromEntity(user));
    }

    /**
     * API 1: Get Jira accountId by email.
     *
     * GET /api/integrations/jira/account-id?email=...
     */
    @GetMapping("/api/integrations/jira/account-id")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> getJiraAccountIdByEmail(@RequestParam("email") @Email @NotBlank String email) {
        String accountId = jiraIntegrationService.findAccountIdByEmail(email);
        return Map.of("accountId", accountId);
    }

    /**
     * Search Jira users by keyword (displayName / email local-part).
     *
     * GET /api/integrations/jira/users/search?query=...
     */
    @GetMapping("/api/integrations/jira/users/search")
    @PreAuthorize("isAuthenticated()")
    public List<JiraIntegrationService.JiraUserResult> searchJiraUsers(@RequestParam("query") @NotBlank String query) {
        return jiraIntegrationService.searchUsers(query);
    }

    /**
     * API 2: Update member integration info (only non-null fields updated).
     *
     * PUT /api/members/{memberId}/integrations
     */
    @PutMapping("/api/members/{memberId}/integrations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MemberIntegrationsResponse> updateMemberIntegrations(
        @PathVariable("memberId") Long memberId,
        @Valid @RequestBody MemberIntegrationsUpdateRequest request
    ) {
        Long actorId = assertCanUpdateMemberIntegrations(memberId);
        User updated = userService.updateMemberIntegrations(memberId, actorId, request.jiraAccountId(), request.githubUsername());
        return ResponseEntity.status(HttpStatus.OK).body(MemberIntegrationsResponse.fromEntity(updated));
    }

    /**
     * API 3: Auto-link Jira accountId by member email.
     *
     * POST /api/members/{memberId}/sync-jira
     */
    @PostMapping("/api/members/{memberId}/sync-jira")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MemberIntegrationsResponse> syncJiraAccountId(@PathVariable("memberId") Long memberId) {
        Long actorId = assertCanUpdateMemberIntegrations(memberId);
        User user = userService.getCurrentUserProfile(memberId);
        String accountId = jiraIntegrationService.findAccountIdByEmail(user.getEmail());
        log.info("accountId from Jira: {}", accountId);
        User updated = userService.updateMemberIntegrations(memberId, actorId, accountId, null);
        return ResponseEntity.status(HttpStatus.OK).body(MemberIntegrationsResponse.fromEntity(updated));
    }

    private Long assertCanUpdateMemberIntegrations(Long memberId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User currentUser)) {
            throw new IllegalArgumentException("Authenticated user not found in security context");
        }
        Long actorId = currentUser.getId();
        if (actorId != null && actorId.equals(memberId)) {
            return actorId; // owner
        }
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_LECTURER")) {
            return actorId;
        }
        throw new org.springframework.security.access.AccessDeniedException("Access denied");
    }
}

