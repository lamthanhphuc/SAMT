package com.samt.projectconfig.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "samt.secrets")
public class ProdSecretsProperties {
    @NotBlank(message = "INTERNAL_SIGNING_SECRET environment variable must be set (non-blank)")
    private String internalSigningSecret;

    @NotBlank(message = "SPRING_DATASOURCE_PASSWORD environment variable must be set (non-blank)")
    private String datasourcePassword;

    @NotBlank(message = "ENCRYPTION_SECRET_KEY environment variable must be set (non-blank)")
    private String encryptionSecretKey;

    @NotBlank(message = "SERVICE_TO_SERVICE_SYNC_KEY environment variable must be set (non-blank)")
    private String serviceToServiceSyncKey;

    public String getInternalSigningSecret() {
        return internalSigningSecret;
    }

    public void setInternalSigningSecret(String internalSigningSecret) {
        this.internalSigningSecret = internalSigningSecret;
    }

    public String getDatasourcePassword() {
        return datasourcePassword;
    }

    public void setDatasourcePassword(String datasourcePassword) {
        this.datasourcePassword = datasourcePassword;
    }

    public String getEncryptionSecretKey() {
        return encryptionSecretKey;
    }

    public void setEncryptionSecretKey(String encryptionSecretKey) {
        this.encryptionSecretKey = encryptionSecretKey;
    }

    public String getServiceToServiceSyncKey() {
        return serviceToServiceSyncKey;
    }

    public void setServiceToServiceSyncKey(String serviceToServiceSyncKey) {
        this.serviceToServiceSyncKey = serviceToServiceSyncKey;
    }
}
