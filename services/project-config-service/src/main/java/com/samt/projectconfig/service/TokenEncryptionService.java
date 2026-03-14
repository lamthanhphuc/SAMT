package com.samt.projectconfig.service;

import com.samt.projectconfig.exception.EncryptionException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting/decrypting tokens using AES-256-GCM.
 * 
 * Format: {iv_base64}:{ciphertext_base64}:{auth_tag_base64}
 * 
 * Security features:
 * - AES-256 encryption
 * - GCM mode (authenticated encryption)
 * - Unique IV per token
 * - Auth tag for tampering detection
 */
@Service
@Slf4j
public class TokenEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    
    @Value("${encryption.secret-key}")
    private String secretKeyHex;
    
    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @PostConstruct
    public void init() {
        try {
            // Convert hex string to byte array
            byte[] keyBytes = hexStringToByteArray(secretKeyHex);
            
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException(
                    "Encryption key must be 32 bytes (256 bits), got " + keyBytes.length
                );
            }
            
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("Token encryption service initialized with AES-256-GCM");
        } catch (Exception e) {
            log.error("Failed to initialize encryption service", e);
            throw new IllegalStateException("Failed to initialize encryption", e);
        }
    }
    
    /**
     * Encrypt plaintext token.
     * 
     * @param plaintext Raw token (Jira API token or GitHub PAT)
     * @return Encrypted string in format: {iv}:{ciphertext}:{auth_tag}
     * @throws EncryptionException if encryption fails
     */
    public String encrypt(String plaintext) {
        try {
            // Generate random IV for this encryption
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            // Encrypt
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintextBytes);
            
            // GCM mode automatically appends auth tag to ciphertext
            // Format: {IV}:{ciphertext_with_auth_tag}
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext);
            
            return ivBase64 + ":" + ciphertextBase64;
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt token", e);
        }
    }
    
    /**
     * Decrypt encrypted token.
     * 
     * @param encrypted Encrypted string in format: {iv}:{ciphertext}
     * @return Decrypted plaintext token
     * @throws EncryptionException if decryption fails or auth tag invalid
     */
    public String decrypt(String encrypted) {
        try {
            // Parse encrypted string
            String[] parts = encrypted.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted format");
            }
            
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            // Decrypt (GCM mode automatically validates auth tag)
            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            
            return new String(plaintextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new EncryptionException("Failed to decrypt token", e);
        }
    }
    
    /**
     * Generate a random encryption key (for testing/setup).
     * 
     * @return 32-byte hex string
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey key = keyGen.generateKey();
            return byteArrayToHexString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate key", e);
        }
    }
    
    private byte[] hexStringToByteArray(String hex) {
        // Remove any whitespace or hyphens
        hex = hex.replaceAll("[\\s-]", "");
        
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
    
    private static String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
