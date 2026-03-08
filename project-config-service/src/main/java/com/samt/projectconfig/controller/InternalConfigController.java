package com.samt.projectconfig.controller;

import com.samt.projectconfig.dto.response.DecryptedTokensResponse;
import com.samt.projectconfig.service.ProjectConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Internal API Controller for service-to-service communication.
 * 
 * Base path: /internal/project-configs
 * Authentication: Authorization: Bearer <internal-jwt> (recommended: enforce via mTLS in prod)
 */
@RestController
@RequestMapping("/internal/project-configs")
@RequiredArgsConstructor
@Slf4j
public class InternalConfigController {
    
    private final ProjectConfigService service;
    
    /**
     * GET /internal/project-configs/{id}/tokens
     * Get decrypted tokens for Sync Service.
     * 
     * SEC-INTERNAL-03: Return decrypted tokens (no masking)
     * SEC-INTERNAL-04: Only if state = VERIFIED
     */
    @GetMapping("/{id}/tokens")
    @PreAuthorize("authentication != null and authentication.name == 'sync-service'")
    public ResponseEntity<Map<String, Object>> getDecryptedTokens(@PathVariable("id") UUID id) {
        log.info("Internal API: getting decrypted tokens for config {}", id);
        
        DecryptedTokensResponse response = service.getDecryptedTokens(id);
        
        return ResponseEntity.ok(Map.of(
            "data", response,
            "timestamp", Instant.now().toString()
        ));
    }
}
