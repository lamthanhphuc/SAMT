package com.example.gateway.security;

public final class PublicEndpointPaths {

    public static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/swagger-resources/**",
            "/swagger-resources",
            "/identity/v3/api-docs/**",
            "/identity/v3/api-docs",
            "/user-group/v3/api-docs/**",
            "/user-group/v3/api-docs",
            "/project-config/v3/api-docs/**",
            "/project-config/v3/api-docs",
            "/sync/v3/api-docs/**",
            "/sync/v3/api-docs",
            "/analysis/v3/api-docs/**",
            "/analysis/v3/api-docs",
            "/report/v3/api-docs/**",
            "/report/v3/api-docs",
            "/notification/v3/api-docs/**",
            "/notification/v3/api-docs"
    };

    private PublicEndpointPaths() {
    }

    public static boolean isPublicPath(String path) {
        return isAuthPath(path) || isWellKnownPath(path) || isSwaggerPath(path);
    }

    private static boolean isAuthPath(String path) {
        return "/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/refresh".equals(path)
                || "/actuator/health".equals(path);
    }

    private static boolean isWellKnownPath(String path) {
        return "/.well-known/internal-jwks.json".equals(path)
                || "/.well-known/jwks.json".equals(path);
    }

    private static boolean isSwaggerPath(String path) {
        return "/swagger-ui.html".equals(path)
                || "/swagger-resources".equals(path)
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars/")
                || path.startsWith("/swagger-resources/")
                || path.startsWith("/identity/v3/api-docs")
                || path.startsWith("/user-group/v3/api-docs")
                || path.startsWith("/project-config/v3/api-docs")
                || path.startsWith("/sync/v3/api-docs")
                || path.startsWith("/analysis/v3/api-docs")
                || path.startsWith("/report/v3/api-docs")
                || path.startsWith("/notification/v3/api-docs");
    }
}