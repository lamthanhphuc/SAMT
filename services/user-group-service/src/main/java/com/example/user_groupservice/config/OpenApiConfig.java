package com.example.user_groupservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the User & Group Service.
 */
@Configuration
public class OpenApiConfig {
    
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("User & Group Service API")
                        .version("1.0.0")
                        .description("""
                            API for managing users and groups in SAMT system.
                            
                            ## Authentication
                            All endpoints require JWT authentication except health checks.
                            Include the token in Authorization header: `Bearer <token>`
                            
                            ## System Roles
                            - **ADMIN**: Full access to all endpoints
                            - **LECTURER**: Can view students and groups
                            - **STUDENT**: Can view own profile and groups
                            
                            ## Group Roles (different from system roles)
                            - **LEADER**: Group leader
                            - **MEMBER**: Group member
                            """)
                        .contact(new Contact()
                                .name("SAMT Team")
                                .email("samt@example.com")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token from identity-service")));
    }
}
