package com.example.notificationservice.security;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalJwtValidationPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void hasSecureDefaults() {
        InternalJwtValidationProperties properties = new InternalJwtValidationProperties();

        assertThat(properties.getIssuer()).isEqualTo("samt-gateway");
        assertThat(properties.getExpectedService()).isEqualTo("api-gateway");
        assertThat(properties.getClockSkewSeconds()).isEqualTo(30);
    }

    @Test
    void rejectsBlankIssuerAndOutOfRangeClockSkew() {
        InternalJwtValidationProperties properties = new InternalJwtValidationProperties();
        properties.setIssuer(" ");
        properties.setExpectedService(" ");
        properties.setClockSkewSeconds(31);

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("issuer", "expectedService", "clockSkewSeconds");
    }
}
