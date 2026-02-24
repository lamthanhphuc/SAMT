package com.samt.projectconfig.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async configuration for verification execution.
 * 
 * Provides dedicated thread pool for external API verification calls.
 * Enables true thread isolation when combined with @Async annotation.
 * 
 * Thread Pool Sizing (ZERO-QUEUE Strategy):
 * - Core: 100 threads (aligned with bulkhead semaphore)
 * - Max: 100 threads (no burst beyond semaphore limit)
 * - Queue: 0 (immediate fail-fast, no buffering)
 * 
 * Perfect 1:1 alignment with Resilience4j Bulkhead SEMAPHORE (maxConcurrentCalls=100)
 * 
 * Benefits:
 * - HTTP request threads freed immediately
 * - No queue buffering masking overload
 * - Instant rejection when capacity exceeded
 * - True bulkhead isolation
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {
    
    /**
     * Dedicated executor for verification operations.
     * 
     * Used by JiraVerificationService and GitHubVerificationService
     * to execute external API calls asynchronously.
     * 
     * @return Configured thread pool executor
     */
    @Bean(name = "verificationExecutor")
    public Executor verificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size: all threads kept alive (aligned with max)
        executor.setCorePoolSize(100);
        
        // Max pool size: maximum concurrent async executions (aligned with bulkhead semaphore)
        executor.setMaxPoolSize(100);
        
        // Queue capacity: ZERO for perfect alignment with bulkhead semaphore
        // No buffering = immediate fail-fast when overloaded
        executor.setQueueCapacity(0);
        
        // Thread naming for debugging
        executor.setThreadNamePrefix("verify-async-");
        
        // Rejection policy: AbortPolicy throws exception instead of contaminating caller thread
        // Prevents HTTP thread contamination under overload
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        
        // CRITICAL: Enable MDC propagation across async threads
        // Without this, correlation ID will be lost in worker threads
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        // Thread keep-alive: cleanup idle threads after 60s
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(false);
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info("Initialized verification executor: core={}, max={}, queue={} (ZERO-QUEUE), rejection=AbortPolicy", 
            executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * Global exception handler for async methods.
     * 
     * Catches uncaught exceptions from @Async methods to prevent silent failures.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Uncaught async exception in method '{}': {}", 
                method.getName(), ex.getMessage(), ex);
            
            // Log parameters for debugging (avoid logging sensitive data)
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                // Mask sensitive parameters (tokens)
                if (param != null && param.toString().length() > 20) {
                    log.error("Parameter[{}]: [REDACTED]", i);
                } else {
                    log.error("Parameter[{}]: {}", i, param);
                }
            }
        };
    }
}
