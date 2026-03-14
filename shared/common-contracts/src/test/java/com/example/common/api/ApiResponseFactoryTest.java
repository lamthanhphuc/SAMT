package com.example.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseFactoryTest {

    @Test
    void createsSuccessResponseWithData() {
        ApiResponse<String> response = ApiResponseFactory.success(200, "ok", "/api/test", "corr-1", true);

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.error()).isNull();
        assertThat(response.message()).isNull();
        assertThat(response.path()).isEqualTo("/api/test");
        assertThat(response.correlationId()).isEqualTo("corr-1");
        assertThat(response.degraded()).isTrue();
        assertThat(response.timestamp()).isNotBlank();
    }

    @Test
    void createsErrorResponseWithoutData() {
        ApiResponse<Void> response = ApiResponseFactory.error(400, "bad_request", "invalid payload", "/api/test", "corr-2");

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo("bad_request");
        assertThat(response.message()).isEqualTo("invalid payload");
        assertThat(response.path()).isEqualTo("/api/test");
        assertThat(response.correlationId()).isEqualTo("corr-2");
        assertThat(response.degraded()).isNull();
        assertThat(response.timestamp()).isNotBlank();
    }
}
