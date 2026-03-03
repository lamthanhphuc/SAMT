package com.example.gateway.security;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InternalJwksController {

    private final InternalJwkSetProvider jwkSetProvider;

    public InternalJwksController(InternalJwkSetProvider jwkSetProvider) {
        this.jwkSetProvider = jwkSetProvider;
    }

    /**
     * Downstream services fetch internal JWT verification keys from here.
     *
     * Expose ONLY on the mTLS internal network.
     */
    @GetMapping(path = "/.well-known/internal-jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> internalJwks() {
        return jwkSetProvider.getJwkSet().toJSONObject();
    }
}
