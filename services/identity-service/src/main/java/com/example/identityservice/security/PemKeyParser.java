package com.example.identityservice.security;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class PemKeyParser {

    private PemKeyParser() {
    }

    static PrivateKey parseRsaPrivateKeyPkcs8(String pem) {
        byte[] der = parsePemToDer(pem);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA private key (expected PKCS#8 PEM)", e);
        }
    }

    static PublicKey parseRsaPublicKeyX509(String pem) {
        byte[] der = parsePemToDer(pem);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA public key (expected X.509 PEM)", e);
        }
    }

    private static byte[] parsePemToDer(String pem) {
        if (pem == null) {
            throw new IllegalStateException("PEM value is null");
        }
        String normalized = pem
                .replace("\r", "")
                .replaceAll("-----BEGIN ([A-Z ]+)-----", "")
                .replaceAll("-----END ([A-Z ]+)-----", "")
                .replace("\n", "")
                .trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException("PEM value is blank");
        }
        return Base64.getDecoder().decode(normalized);
    }
}
