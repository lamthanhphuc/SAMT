package com.example.identityservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Async configuration for enabling @Async annotation.
 * Used by AuditService for non-blocking audit logging.
 * 
 * @see docs/Authentication-Authorization-Design.md - Section 7.4 Async Processing
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
