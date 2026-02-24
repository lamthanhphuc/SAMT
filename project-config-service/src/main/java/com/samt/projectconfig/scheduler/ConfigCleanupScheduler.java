package com.samt.projectconfig.scheduler;

import com.samt.projectconfig.repository.ProjectConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled job to clean up soft-deleted configurations.
 * 
 * BR-DELETE-03: Hard delete configs after 90-day retention.
 * Runs daily at 2 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigCleanupScheduler {
    
    private final ProjectConfigRepository repository;
    
    @Value("${cleanup.retention-days:90}")
    private int retentionDays;
    
    /**
     * Hard delete configs older than retention period.
     * Cron: Daily at 2 AM.
     */
    @Scheduled(cron = "${cleanup.cron:0 0 2 * * *}")
    @Transactional
    public void cleanupExpiredConfigs() {
        log.info("Starting cleanup job for configs older than {} days", retentionDays);
        
        Instant cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        
        int deleted = repository.hardDeleteExpiredConfigs(cutoffDate);
        
        if (deleted > 0) {
            log.warn("Permanently deleted {} expired configurations", deleted);
        } else {
            log.info("No expired configurations to clean up");
        }
    }
}
