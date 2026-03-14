package com.example.notificationservice.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProdSecretsPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsNonBlankDatasourcePassword() {
        ProdSecretsProperties properties = new ProdSecretsProperties();
        properties.setDatasourcePassword("strong-secret");

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsBlankDatasourcePassword() {
        ProdSecretsProperties properties = new ProdSecretsProperties();
        properties.setDatasourcePassword(" ");

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("datasourcePassword");
    }
}
