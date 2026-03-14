package com.example.identityservice.config;

import com.example.identityservice.security.JwtKeyMaterial;
import com.example.identityservice.security.RsaKeyLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.security.PrivateKey;
import java.security.PublicKey;

@Configuration
public class JwtKeyConfiguration {

    @Bean
    public JwtKeyMaterial jwtKeyMaterial(
            @Value("${jwt.key.id:${JWT_KEY_ID:}}") String keyId,
            @Value("${jwt.key.private-key-pem:${JWT_PRIVATE_KEY_PEM:}}") String privateKeyPem,
            @Value("${jwt.key.private-key-path:${JWT_PRIVATE_KEY_PATH:}}") String privateKeyPath,
            @Value("${jwt.key.public-key-pem:${JWT_PUBLIC_KEY_PEM:}}") String publicKeyPem,
            @Value("${jwt.key.public-key-path:${JWT_PUBLIC_KEY_PATH:}}") String publicKeyPath
    ) {
        if (!StringUtils.hasText(keyId)) {
            throw new IllegalStateException("JWT_KEY_ID must be set (non-blank).");
        }

        PrivateKey privateKey = RsaKeyLoader.loadPrivateKey(privateKeyPem, privateKeyPath);
        PublicKey publicKey = RsaKeyLoader.loadOrDerivePublicKey(privateKey, publicKeyPem, publicKeyPath);
        return new JwtKeyMaterial(privateKey, publicKey, keyId);
    }
}
