package com.example.identityservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "samt.secrets")
public class ProdSecretsProperties {

    @NotBlank(message = "SPRING_DATASOURCE_PASSWORD environment variable must be set (non-blank)")
    private String datasourcePassword;

    public String getDatasourcePassword() {
        return datasourcePassword;
    }

    public void setDatasourcePassword(String datasourcePassword) {
        this.datasourcePassword = datasourcePassword;
    }
}
