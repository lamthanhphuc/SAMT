package com.example.syncservice.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuration for ShedLock distributed locking.
 * 
 * CRITICAL: Ensures only ONE instance executes scheduled jobs in multi-replica deployments.
 * 
 * How it works:
 * - Before @Scheduled method executes, ShedLock acquires DB lock
 * - If lock acquired: Execute method
 * - If lock NOT acquired: Skip execution (another instance is running)
 * - Lock automatically released after execution or timeout
 * 
 * Database table: shedlock (created by migration V5)
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class ShedLockConfig {

    /**
     * Configure lock provider using JDBC.
     * Uses PostgreSQL for lock storage.
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // Use database time for consistency across instances
                .build());
    }
}
