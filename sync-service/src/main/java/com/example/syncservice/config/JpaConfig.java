package com.example.syncservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration.
 * 
 * Enables JPA auditing for automatic created_at, updated_at timestamps.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
