package com.example.gateway.security;

import com.nimbusds.jose.jwk.RSAKey;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public record InternalJwtKeyMaterial(
        RSAPrivateKey privateKey,
        RSAPublicKey publicKey,
        RSAKey jwk
) {
}
