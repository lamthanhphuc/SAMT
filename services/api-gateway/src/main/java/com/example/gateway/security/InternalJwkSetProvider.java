package com.example.gateway.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides the JWKS published at {@code /.well-known/internal-jwks.json}.
 *
 * Supports key rotation by optionally publishing additional public keys from a JWKS JSON file.
 */
@Component
public class InternalJwkSetProvider {

    private final InternalJwtProperties props;
    private final InternalJwtKeyMaterial keyMaterial;

    public InternalJwkSetProvider(InternalJwtProperties props, InternalJwtKeyMaterial keyMaterial) {
        this.props = props;
        this.keyMaterial = keyMaterial;
    }

    public JWKSet getJwkSet() {
        List<JWK> keys = new ArrayList<>();
        keys.add(keyMaterial.jwk());

        String additionalPath = props.getAdditionalPublicJwksJsonPath();
        if (StringUtils.hasText(additionalPath)) {
            keys.addAll(loadAdditionalPublicKeys(Path.of(additionalPath)));
        }

        return new JWKSet(keys);
    }

    private static List<JWK> loadAdditionalPublicKeys(Path jwksJsonPath) {
        try {
            String json = Files.readString(jwksJsonPath, StandardCharsets.UTF_8);
            JWKSet set = JWKSet.parse(json);
            return set.getKeys();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read additional JWKS file: " + jwksJsonPath, e);
        } catch (ParseException e) {
            throw new IllegalStateException("Invalid JWKS JSON in: " + jwksJsonPath, e);
        }
    }
}
