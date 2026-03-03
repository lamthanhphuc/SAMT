package com.example.identityservice.controller;

import com.example.identityservice.security.JwtKeyMaterial;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public JWKS endpoint for API Gateway JWT validation.
 *
 * Endpoint: /.well-known/jwks.json
 * Returns PUBLIC keys only.
 */
@RestController
public class JwksController {

    private final JwtKeyMaterial keyMaterial;

    public JwksController(JwtKeyMaterial keyMaterial) {
        this.keyMaterial = keyMaterial;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        if (!(keyMaterial.publicKey() instanceof RSAPublicKey rsaPublicKey)) {
            throw new IllegalStateException("JWT public key is not RSA");
        }

        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyMaterial.keyId());
        jwk.put("n", base64UrlUnsigned(rsaPublicKey.getModulus()));
        jwk.put("e", base64UrlUnsigned(rsaPublicKey.getPublicExponent()));
        jwk.put("key_ops", List.of("verify"));

        return Map.of("keys", List.of(jwk));
    }

    private static String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
