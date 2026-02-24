package com.example.syncservice.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async configuration for bounded thread pool execution.
 * 
 * CRITICAL: Without this configuration, Spring uses SimpleAsyncTaskExecutor
 * which creates unbounded threads and will cause OutOfMemoryError under load.
 * 
 * Production Safety:
 * - Core pool: 2 threads (always alive)
 * - Max pool: 5 threads (scale up when needed)
 * - Queue: 100 tasks (bounded queue prevents memory exhaustion)
 * - Rejection: CallerRunsPolicy (backpressure - caller thread executes if queue full)
 * 
 * @see com.example.syncservice.SyncServiceApplication
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {
    
    @Bean(name = "syncTaskExecutor")
    public Executor syncTaskExecutor(
            @Value("${sync.async.core-pool-size:2}") int corePoolSize,
            @Value("${sync.async.max-pool-size:5}") int maxPoolSize,
            @Value("${sync.async.queue-capacity:100}") int queueCapacity,
            @Value("${sync.async.thread-name-prefix:sync-}") String threadNamePrefix) {
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size: Threads always alive waiting for tasks
        executor.setCorePoolSize(corePoolSize);
        
        // Max pool size: Maximum threads that can be created
        executor.setMaxPoolSize(maxPoolSize);
        
        // Queue capacity: Pending tasks before rejection
        executor.setQueueCapacity(queueCapacity);
        
        // Thread naming for debugging
        executor.setThreadNamePrefix(threadNamePrefix);
        
        // CRITICAL: Rejection policy prevents scheduler thread deadlock
        // AbortPolicy = Throw RejectedExecutionException if queue full (must handle explicitly)
        // WHY NOT CallerRunsPolicy: Would block scheduler thread, causing ShedLock timeout mismatch
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        
        // Graceful shutdown: Wait for tasks to complete before shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // MDC propagation: Correlation IDs propagate to async threads
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.initialize();
        
        log.info("✅ Initialized syncTaskExecutor - core={}, max={}, queue={}, prefix='{}'",
                corePoolSize, maxPoolSize, queueCapacity, threadNamePrefix);
        log.info("✅ Rejection policy: AbortPolicy (prevents scheduler thread deadlock)");
        log.info("✅ MDC propagation: ENABLED (correlation IDs propagate to async threads)");
        
        return executor;
    }
    
    /**
     * Task decorator for propagating MDC context (correlation IDs) to async threads.
     * Without this, correlation IDs would be lost in async execution.
     */
    public static class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // Capture MDC from parent thread
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            
            return () -> {
                try {
                    // Restore MDC in async thread
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    // Clean up MDC after execution
                    MDC.clear();
                }
            };
        }
    }
}
