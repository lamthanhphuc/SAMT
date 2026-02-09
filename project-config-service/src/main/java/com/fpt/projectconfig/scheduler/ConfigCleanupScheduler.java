package com.fpt.projectconfig.scheduler;

import com.fpt.projectconfig.repository.ProjectConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled job để hard delete configs đã soft delete sau 90 ngày
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigCleanupScheduler {

    private final ProjectConfigRepository repository;

    /**
     * Chạy mỗi ngày lúc 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldDeletedConfigs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        
        int deletedCount = repository.deleteOldSoftDeletedConfigs(cutoffDate);
        
        if (deletedCount > 0) {
            log.info("Cleanup job: Hard deleted {} configs older than 90 days", deletedCount);
        } else {
            log.debug("Cleanup job: No configs to delete");
        }
    }
}
