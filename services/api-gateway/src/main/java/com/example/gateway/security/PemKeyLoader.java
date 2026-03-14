package com.example.gateway.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Minimal PEM loader for PKCS#8 RSA private keys.
 */
public final class PemKeyLoader {

    private PemKeyLoader() {
    }

    public static RSAPrivateKey loadRsaPrivateKeyPkcs8(Path pemPath) {
        try {
            String pem = Files.readString(pemPath, StandardCharsets.UTF_8);
            byte[] der = parsePem(pem, "PRIVATE KEY");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey key = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateKey rsaKey)) {
                throw new IllegalArgumentException("Not an RSA private key: " + pemPath);
            }
            return rsaKey;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read PEM file: " + pemPath, e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse RSA private key: " + pemPath, e);
        }
    }

    public static RSAPublicKey deriveRsaPublicKey(RSAPrivateKey privateKey) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");

            // If we have CRT key, we can derive public key reliably.
            if (privateKey instanceof RSAPrivateCrtKey crt) {
                PublicKey publicKey = kf.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
                if (!(publicKey instanceof RSAPublicKey rsaPublic)) {
                    throw new IllegalStateException("Derived key is not RSAPublicKey");
                }
                return rsaPublic;
            }

            throw new IllegalArgumentException("Private key is not RSAPrivateCrtKey; provide a matching public key instead");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive RSA public key", e);
        }
    }

    private static byte[] parsePem(String pem, String type) {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";

        int start = pem.indexOf(begin);
        int finish = pem.indexOf(end);
        if (start < 0 || finish < 0) {
            throw new IllegalArgumentException("PEM does not contain type " + type);
        }

        String base64 = pem.substring(start + begin.length(), finish)
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
