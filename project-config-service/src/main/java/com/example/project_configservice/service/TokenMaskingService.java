package com.example.project_configservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for masking sensitive tokens in API responses.
 * 
 * Masking Rules:
 * - Jira token: Show first 3 chars + "***" + last 4 chars (e.g., "ATA***ab12")
 * - GitHub token: Show "ghp_***" + last 4 chars (e.g., "ghp_***xyz9")
 */
@Service
@Slf4j
public class TokenMaskingService {
    
    /**
     * Mask Jira API token for display.
     * Format: ATATT3xFfGF0... → ATA***ab12
     */
    public String maskJiraToken(String encryptedToken) {
        // Note: In real implementation, decrypt first then mask
        // For simplicity, we'll mask the encrypted string itself
        if (encryptedToken == null || encryptedToken.length() < 8) {
            return "***";
        }
        
        String prefix = encryptedToken.substring(0, Math.min(3, encryptedToken.length()));
        String suffix = encryptedToken.substring(Math.max(0, encryptedToken.length() - 4));
        
        return prefix + "***" + suffix;
    }
    
    /**
     * Mask GitHub token for display.
     * Format: ghp_1234567890... → ghp_***xyz9
     */
    public String maskGithubToken(String encryptedToken) {
        // Note: In real implementation, decrypt first then mask
        // For simplicity, we'll mask the encrypted string itself
        if (encryptedToken == null || encryptedToken.length() < 8) {
            return "ghp_***";
        }
        
        String suffix = encryptedToken.substring(Math.max(0, encryptedToken.length() - 4));
        
        return "ghp_***" + suffix;
    }
}
