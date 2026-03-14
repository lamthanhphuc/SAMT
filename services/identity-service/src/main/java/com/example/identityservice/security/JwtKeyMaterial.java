package com.example.identityservice.security;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Immutable JWT key material used by Identity Service.
 *
 * - privateKey: used for RS256 signing
 * - publicKey: used for local verification + JWKS publishing
 * - keyId: exposed as JWT header 'kid' and JWKS 'kid'
 */
public record JwtKeyMaterial(PrivateKey privateKey, PublicKey publicKey, String keyId) {
}
