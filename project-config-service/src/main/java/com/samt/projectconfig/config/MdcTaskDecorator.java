package com.samt.projectconfig.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * TaskDecorator to propagate MDC (Mapped Diagnostic Context) across thread boundaries.
 * 
 * Problem Statement:
 * - MDC uses ThreadLocal storage (thread-isolated by design)
 * - @Async methods execute on worker threads from ThreadPoolTaskExecutor
 * - Parent thread's MDC context (correlation ID) NOT automatically inherited
 * - Without propagation, 90% of logs will have null correlation ID
 * 
 * Solution:
 * - Capture MDC context from parent thread (HTTP request thread)
 * - Restore MDC context in worker thread before task execution
 * - Clear MDC after task completion to prevent memory leak
 * 
 * Thread Lifecycle:
 * 1. HTTP request arrives on Tomcat thread
 * 2. CorrelationIdFilter puts correlation ID into MDC
 * 3. @Async method called → task submitted to ThreadPoolTaskExecutor
 * 4. TaskDecorator.decorate() called on HTTP thread → captures MDC
 * 5. Worker thread picks up task → restored MDC before run()
 * 6. Task executes with correlation ID in MDC
 * 7. finally block clears MDC → prevents leak in pooled thread
 * 
 * Memory Safety:
 * - ThreadPoolTaskExecutor reuses worker threads (long-lived)
 * - ThreadLocal values persist until explicitly cleared
 * - MDC.clear() in finally prevents memory leak
 * - Null-safe: handles case when parent thread has no MDC context
 * 
 * Scope:
 * - Applied to verificationExecutor (100 threads)
 * - Covers JiraVerificationService.verifyAsync()
 * - Covers GitHubVerificationService.verifyAsync()
 * - Covers Resilience4j Retry (same executor)
 * - Covers CompletableFuture chains (same executor)
 * 
 * NOT Required For:
 * - Semaphore bulkhead (executes on caller thread)
 * - gRPC calls (run on Netty event loop, propagated via Metadata)
 * 
 * Performance:
 * - Minimal overhead: single HashMap copy per async call
 * - No serialization or deep cloning
 * - Executed once per task submission
 * 
 * @author Production Team
 * @version 1.0
 */
public class MdcTaskDecorator implements TaskDecorator {
    
    /**
     * Decorate a Runnable to propagate MDC context across threads.
     * 
     * Execution Timeline:
     * 1. Called on parent thread (HTTP request thread)
     * 2. Captures MDC.getCopyOfContextMap() → shallow copy of HashMap
     * 3. Returns decorated Runnable
     * 4. Decorated Runnable executed on worker thread
     * 5. Captures any previous MDC context in worker thread (defensive)
     * 6. Restores parent MDC context
     * 7. Runs original task
     * 8. Restores previous worker thread context (or clears if none)
     * 
     * Defensive Pattern:
     * - Preserves existing worker thread MDC context (nested async safe)
     * - Restores worker context after task completion
     * - Prevents context contamination between tasks
     * - Handles nested async execution correctly
     * 
     * Null Safety:
     * - getCopyOfContextMap() returns null if no MDC context exists
     * - setContextMap(null) is a no-op (safe to call)
     * - clear() always safe to call
     * 
     * @param runnable Original task submitted to executor
     * @return Decorated runnable with MDC propagation
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        // Capture MDC context from parent thread (HTTP request thread)
        // This executes synchronously before task is queued
        Map<String, String> parentContext = MDC.getCopyOfContextMap();
        
        return () -> {
            // Capture any existing MDC context in worker thread (defensive)
            // Protects against nested async or thread pool reuse scenarios
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            
            try {
                // Restore parent thread MDC context in worker thread
                // Clear if parent has no context (avoid stale data)
                if (parentContext != null) {
                    MDC.setContextMap(parentContext);
                } else {
                    MDC.clear();
                }
                
                // Execute original task with restored MDC context
                // correlation ID now available in logs
                runnable.run();
                
            } finally {
                // CRITICAL: Restore previous worker thread context (enterprise pattern)
                // Ensures nested async or thread reuse doesn't lose context
                // If no previous context existed, clear to prevent leak
                if (previousContext != null) {
                    MDC.setContextMap(previousContext);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
