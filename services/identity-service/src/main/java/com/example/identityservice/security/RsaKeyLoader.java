package com.example.identityservice.security;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.KeyFactory;

public final class RsaKeyLoader {

    private RsaKeyLoader() {
    }

    public static PrivateKey loadPrivateKey(String privateKeyPem, String privateKeyPath) {
        String pem = firstNonBlank(privateKeyPem, readFileIfPresent(privateKeyPath));
        if (!StringUtils.hasText(pem)) {
            throw new IllegalStateException("JWT private key is missing. Provide JWT_PRIVATE_KEY_PEM or JWT_PRIVATE_KEY_PATH.");
        }
        return PemKeyParser.parseRsaPrivateKeyPkcs8(pem);
    }

    public static PublicKey loadOrDerivePublicKey(PrivateKey privateKey, String publicKeyPem, String publicKeyPath) {
        String pem = firstNonBlank(publicKeyPem, readFileIfPresent(publicKeyPath));
        if (StringUtils.hasText(pem)) {
            return PemKeyParser.parseRsaPublicKeyX509(pem);
        }

        if (privateKey instanceof RSAPrivateCrtKey crtKey) {
            try {
                RSAPublicKeySpec spec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to derive RSA public key from private key", e);
            }
        }

        throw new IllegalStateException("JWT public key is missing and cannot be derived. Provide JWT_PUBLIC_KEY_PEM or JWT_PUBLIC_KEY_PATH.");
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        if (StringUtils.hasText(b)) {
            return b;
        }
        return null;
    }

    private static String readFileIfPresent(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read key file at path: " + path, e);
        }
    }
}
