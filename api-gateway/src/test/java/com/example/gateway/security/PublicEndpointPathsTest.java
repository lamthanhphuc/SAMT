package com.example.gateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicEndpointPathsTest {

    @Test
    void swaggerEndpointsArePublic() {
        assertThat(PublicEndpointPaths.isPublicPath("/swagger-ui/index.html")).isTrue();
        assertThat(PublicEndpointPaths.isPublicPath("/swagger-ui.html")).isTrue();
        assertThat(PublicEndpointPaths.isPublicPath("/v3/api-docs/swagger-config")).isTrue();
        assertThat(PublicEndpointPaths.isPublicPath("/webjars/swagger-ui/index.html")).isTrue();
        assertThat(PublicEndpointPaths.isPublicPath("/swagger-resources")).isTrue();
        assertThat(PublicEndpointPaths.isPublicPath("/swagger-resources/configuration/ui")).isTrue();
    }

    @Test
    void normalApisRemainProtected() {
        assertThat(PublicEndpointPaths.isPublicPath("/api/groups")).isFalse();
        assertThat(PublicEndpointPaths.isPublicPath("/api/project-configs/123")).isFalse();
        assertThat(PublicEndpointPaths.isPublicPath("/api/admin/users")).isFalse();
    }
}