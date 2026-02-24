package com.samt.projectconfig.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for masking sensitive tokens in API responses.
 * 
 * Masking rules:
 * - Jira token: Show first 3 chars + "***" + last 4 chars (e.g., "ATA***ab12")
 * - GitHub token: Show prefix "ghp_***" + last 4 chars (e.g., "ghp_***xyz9")
 * 
 * Security: Never expose full tokens to frontend to prevent accidental logging/leakage.
 */
@Service
@Slf4j
public class TokenMaskingService {
    
    /**
     * Mask Jira API token.
     * 
     * @param encryptedToken Encrypted token from database
     * @param decryptedToken Decrypted plaintext token
     * @return Masked token (e.g., "ATA***ab12")
     */
    public String maskJiraToken(String decryptedToken) {
        if (decryptedToken == null || decryptedToken.length() < 8) {
            return "***";
        }
        
        // Show first 3 chars + *** + last 4 chars
        String prefix = decryptedToken.substring(0, Math.min(3, decryptedToken.length()));
        String suffix = decryptedToken.substring(Math.max(0, decryptedToken.length() - 4));
        
        return prefix + "***" + suffix;
    }
    
    /**
     * Mask GitHub Personal Access Token.
     * 
     * @param decryptedToken Decrypted plaintext token
     * @return Masked token (e.g., "ghp_***xyz9")
     */
    public String maskGithubToken(String decryptedToken) {
        if (decryptedToken == null || decryptedToken.length() < 8) {
            return "ghp_***";
        }
        
        // Show "ghp_***" + last 4 chars
        String suffix = decryptedToken.substring(Math.max(0, decryptedToken.length() - 4));
        
        return "ghp_***" + suffix;
    }
}
