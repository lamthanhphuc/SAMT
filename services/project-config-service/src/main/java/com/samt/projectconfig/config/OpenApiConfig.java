package com.samt.projectconfig.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI projectConfigOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ProjectConfig Service API")
                        .version("1.0.0")
                        .description("""
                                Manages Jira and GitHub configurations for user groups in SAMT.
                                
                                ## Features
                                - 🔐 JWT Authentication
                                - 🔒 AES-256-GCM Token Encryption
                                - 🎭 Token Masking in Responses
                                - ✅ Credential Verification (Jira/GitHub)
                                - 🗑️ Soft Delete with 90-day Retention
                                - 🔄 State Machine: DRAFT → VERIFIED → INVALID → DELETED
                                
                                ## Authentication
                                All `/api/**` endpoints require JWT Bearer token.
                                
                                Get JWT from Identity Service first, then click **Authorize** button above.
                                """)
                        .contact(new Contact()
                                .name("SAMT Backend Team")
                                .email("backend@samt.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token from Identity Service")));
    }
}
