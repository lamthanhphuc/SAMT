package com.samt.projectconfig.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "samt.secrets")
public class ProdSecretsProperties {
    @NotBlank(message = "SPRING_DATASOURCE_PASSWORD environment variable must be set (non-blank)")
    private String datasourcePassword;

    @NotBlank(message = "ENCRYPTION_SECRET_KEY environment variable must be set (non-blank)")
    private String encryptionSecretKey;

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
}
