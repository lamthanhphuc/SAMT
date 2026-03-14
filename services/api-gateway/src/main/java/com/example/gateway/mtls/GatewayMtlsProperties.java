package com.example.gateway.mtls;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.mtls")
public class GatewayMtlsProperties {

    /**
     * When enabled, the gateway HTTP client (Spring Cloud Gateway -> downstream) uses mTLS.
     */
    private boolean enabled = false;

    /**
     * Name of the Spring Boot SSL bundle to use for the gateway HTTP client.
     */
    @NotBlank
    private String clientBundle = "gateway-client";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getClientBundle() {
        return clientBundle;
    }

    public void setClientBundle(String clientBundle) {
        this.clientBundle = clientBundle;
    }
}
