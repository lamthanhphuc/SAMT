package com.fpt.projectconfig.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service mask tokens dựa trên role
 * Jira: ***last4chars
 * GitHub: ghp_***last4chars
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenMaskingService {

    private final TokenEncryptionService encryptionService;

    public String maskToken(String encryptedToken, boolean canSeeFullToken) {
        if (encryptedToken == null || encryptedToken.isBlank()) {
            return null;
        }

        String decrypted = encryptionService.decrypt(encryptedToken);

        if (canSeeFullToken) {
            return decrypted;
        }

        return maskTokenString(decrypted);
    }

    private String maskTokenString(String token) {
        if (token == null || token.length() < 4) {
            return "***";
        }

        String last4 = token.substring(token.length() - 4);

        if (token.startsWith("ATATT")) {
            return "***" + last4; // Jira token
        } else if (token.startsWith("ghp_")) {
            return "ghp_***" + last4; // GitHub token
        } else {
            return "***";
        }
    }
}
