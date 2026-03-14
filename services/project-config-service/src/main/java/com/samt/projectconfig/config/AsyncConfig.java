package com.samt.projectconfig.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    public Executor verificationExecutor(
        @Value("${verification.async.core-pool-size:24}") int corePoolSize,
        @Value("${verification.async.max-pool-size:48}") int maxPoolSize,
        @Value("${verification.async.queue-capacity:300}") int queueCapacity,
        @Value("${verification.async.keep-alive-seconds:60}") int keepAliveSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(corePoolSize);
        
        executor.setMaxPoolSize(maxPoolSize);
        
        executor.setQueueCapacity(queueCapacity);
        
        // Thread naming for debugging
        executor.setThreadNamePrefix("verify-async-");
        
        // Rejection policy: AbortPolicy throws exception instead of contaminating caller thread
        // Prevents HTTP thread contamination under overload
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        
        // CRITICAL: Enable MDC propagation across async threads
        // Without this, correlation ID will be lost in worker threads
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setAllowCoreThreadTimeOut(false);
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info(
            "Initialized verification executor: core={}, max={}, queue={}, keepAlive={}s, rejection=AbortPolicy",
            executor.getCorePoolSize(),
            executor.getMaxPoolSize(),
            executor.getQueueCapacity(),
            keepAliveSeconds
        );
        
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
