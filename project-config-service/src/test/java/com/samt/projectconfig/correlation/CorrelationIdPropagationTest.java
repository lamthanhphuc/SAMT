package com.samt.projectconfig.correlation;

import com.samt.projectconfig.config.AsyncConfig;
import com.samt.projectconfig.config.MdcTaskDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify MDC (correlation ID) propagation across async thread boundaries.
 * 
 * Test Objective:
 * Validate that correlation ID set in parent thread (HTTP request thread)
 * is correctly propagated to worker threads via MdcTaskDecorator.
 * 
 * Test Scenarios:
 * 1. Correlation ID propagates from parent thread to async executor thread
 * 2. Multiple concurrent async tasks maintain isolated correlation IDs
 * 3. MDC context is properly cleaned up after task execution
 * 4. Null correlation ID is handled safely
 * 
 * Architecture Under Test:
 * - HTTP Request Thread → sets MDC
 * - @Async method → submits task to verificationExecutor
 * - MdcTaskDecorator → captures MDC context
 * - Worker Thread → restores MDC context
 * - Task Execution → accesses correlation ID
 * - Finally Block → clears MDC
 * 
 * @author Production Team
 * @version 1.0
 */
class CorrelationIdPropagationTest {
    
    private Executor verificationExecutor;
    private static final String MDC_KEY = "correlationId";
    
    /**
     * Setup test executor with MdcTaskDecorator.
     * Mirrors production AsyncConfig configuration.
     */
    @BeforeEach
    void setUp() {
        // Create executor identical to AsyncConfig.verificationExecutor
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);  // Reduced for testing
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("test-verify-");
        
        // CRITICAL: Enable MDC propagation
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        
        this.verificationExecutor = executor;
    }
    
    /**
     * Cleanup MDC after each test to prevent pollution.
     */
    @AfterEach
    void tearDown() {
        MDC.clear();
        
        // Shutdown executor
        if (verificationExecutor instanceof ThreadPoolTaskExecutor executor) {
            executor.shutdown();
        }
    }
    
    /**
     * Test MDC propagation across async boundary.
     * 
     * Flow:
     * 1. Set correlation ID in parent thread (simulates HTTP request)
     * 2. Submit async task to verificationExecutor
     * 3. Capture correlation ID from worker thread
     * 4. Assert correlation ID matches original
     * 
     * This validates:
     * - MdcTaskDecorator captures MDC context from parent thread
     * - Worker thread receives restored MDC context
     * - Correlation ID available in async execution
     */
    @Test
    void shouldPropagateMdcAcrossAsyncBoundary() throws ExecutionException, InterruptedException {
        // ARRANGE: Set correlation ID in parent thread (HTTP request thread)
        String testCorrelationId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, testCorrelationId);
        
        // ACT: Execute async operation that reads MDC
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
            () -> {
                // This executes on worker thread
                // MDC should be restored by MdcTaskDecorator
                String capturedId = MDC.get(MDC_KEY);
                
                // Simulate async verification work
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                return capturedId;
            },
            verificationExecutor
        );
        
        // ASSERT: Correlation ID propagated to worker thread
        String capturedId = future.get();
        
        assertNotNull(capturedId, 
            "Correlation ID must not be null in worker thread");
        assertEquals(testCorrelationId, capturedId,
            "Correlation ID must propagate from parent thread to async executor thread via TaskDecorator");
    }
    
    /**
     * Test MDC propagation through CompletableFuture chain.
     * 
     * Flow:
     * 1. Set correlation ID in parent thread
     * 2. Chain multiple async operations: supplyAsync → thenApplyAsync
     * 3. Verify correlation ID available in each stage
     * 
     * CRITICAL: Use thenApplyAsync with explicit executor to guarantee worker thread execution.
     * thenApply() may execute on caller thread if future already complete.
     * 
     * This validates:
     * - MDC propagates through CompletableFuture chains
     * - Each stage in chain has access to correlation ID
     * - Mimics real verification flow (async → transform → result)
     */
    @Test
    void shouldPropagateMdcThroughCompletableFutureChain() throws ExecutionException, InterruptedException {
        // ARRANGE: Set correlation ID in parent thread
        String testCorrelationId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, testCorrelationId);
        
        // ACT: Chain async operations (simulates verification flow)
        CompletableFuture<String> future = CompletableFuture
            .supplyAsync(() -> {
                // Stage 1: Simulate Jira/GitHub verification
                String stage1Id = MDC.get(MDC_KEY);
                assertNotNull(stage1Id, "Stage 1: MDC must be propagated");
                
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                return "verification-result";
            }, verificationExecutor)
            .thenApplyAsync(result -> {
                // Stage 2: Transform result (simulates DTO mapping)
                // CRITICAL: thenApplyAsync with explicit executor guarantees worker thread
                String stage2Id = MDC.get(MDC_KEY);
                assertNotNull(stage2Id, "Stage 2: MDC must be propagated");
                
                return stage2Id;
            }, verificationExecutor);
        
        // ASSERT: Correlation ID available in all stages
        String capturedId = future.get();
        
        assertEquals(testCorrelationId, capturedId,
            "Correlation ID must propagate through entire CompletableFuture chain");
    }
    
    /**
     * Test multiple concurrent async tasks maintain isolated correlation IDs.
     * 
     * Flow:
     * 1. Submit 5 tasks with different correlation IDs
     * 2. Each task captures its correlation ID
     * 3. Verify each task sees its own correlation ID
     * 
     * This validates:
     * - MDC context isolation between threads
     * - No cross-contamination of correlation IDs
     * - Thread-safe MDC propagation under concurrency
     */
    @Test
    void shouldMaintainIsolatedCorrelationIdsForConcurrentTasks() throws InterruptedException {
        // ARRANGE: Create 5 tasks with different correlation IDs
        int taskCount = 5;
        CompletableFuture<Void>[] futures = new CompletableFuture[taskCount];
        String[] expectedIds = new String[taskCount];
        
        // ACT: Submit concurrent tasks with different correlation IDs
        for (int i = 0; i < taskCount; i++) {
            final String taskCorrelationId = "task-" + i + "-" + UUID.randomUUID();
            expectedIds[i] = taskCorrelationId;
            
            // Each task sets its own correlation ID and verifies isolation
            futures[i] = CompletableFuture.runAsync(() -> {
                // Set correlation ID in parent scope (simulates different HTTP requests)
                MDC.put(MDC_KEY, taskCorrelationId);
                
                // Submit async work
                CompletableFuture<String> asyncFuture = CompletableFuture.supplyAsync(
                    () -> {
                        String capturedId = MDC.get(MDC_KEY);
                        
                        // Simulate work
                        try {
                            TimeUnit.MILLISECONDS.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        return capturedId;
                    },
                    verificationExecutor
                );
                
                // Verify isolation
                try {
                    String capturedId = asyncFuture.get();
                    assertEquals(taskCorrelationId, capturedId,
                        "Task " + taskCorrelationId + " must see its own correlation ID");
                } catch (Exception e) {
                    fail("Failed to capture correlation ID: " + e.getMessage());
                } finally {
                    MDC.clear();
                }
            });
        }
        
        // ASSERT: All tasks completed successfully
        CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
    }
    
    /**
     * Test null correlation ID is handled safely.
     * 
     * Flow:
     * 1. Do NOT set correlation ID in parent thread
     * 2. Submit async task
     * 3. Verify no NullPointerException
     * 4. Verify null MDC context is propagated safely
     * 
     * This validates:
     * - MdcTaskDecorator null-safety
     * - getCopyOfContextMap() returns null handled correctly
     * - setContextMap(null) does not throw exception
     */
    @Test
    void shouldHandleNullCorrelationIdSafely() throws ExecutionException, InterruptedException {
        // ARRANGE: Clear MDC (no correlation ID set)
        MDC.clear();
        
        // ACT: Execute async operation without correlation ID
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
            () -> {
                // Should not throw NullPointerException
                String capturedId = MDC.get(MDC_KEY);
                
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                return capturedId;
            },
            verificationExecutor
        );
        
        // ASSERT: Null correlation ID handled safely
        String capturedId = future.get();
        
        assertNull(capturedId,
            "Correlation ID should be null when not set in parent thread");
        
        // Test completed without exception = null-safe ✅
    }
    
    /**
     * Test MDC cleanup after task execution.
     * 
     * Flow:
     * 1. Submit task that sets correlation ID
     * 2. Task completes
     * 3. Submit another task on same thread (thread pool reuse)
     * 4. Verify second task does not see first task's correlation ID
     * 
     * This validates:
     * - MdcTaskDecorator clears MDC in finally block
     * - No ThreadLocal memory leak
     * - Thread pool reuse is safe
     * 
     * Note: This test is probabilistic (depends on thread pool reuse).
     * Reduced core pool size (10) increases reuse probability.
     */
    @Test
    void shouldCleanUpMdcAfterTaskExecution() throws ExecutionException, InterruptedException {
        // ARRANGE: First task with correlation ID
        String firstCorrelationId = "first-" + UUID.randomUUID();
        MDC.put(MDC_KEY, firstCorrelationId);
        
        // ACT: Submit first task
        CompletableFuture<String> firstFuture = CompletableFuture.supplyAsync(
            () -> {
                String capturedId = MDC.get(MDC_KEY);
                
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                return capturedId;
            },
            verificationExecutor
        );
        
        String firstCapturedId = firstFuture.get();
        assertEquals(firstCorrelationId, firstCapturedId, "First task should capture its correlation ID");
        
        // Wait for first task to complete and cleanup
        TimeUnit.MILLISECONDS.sleep(100);
        
        // ARRANGE: Second task WITHOUT correlation ID (simulates different request)
        MDC.clear();  // Clear parent thread MDC
        
        // ACT: Submit second task (likely reuses same worker thread)
        CompletableFuture<String> secondFuture = CompletableFuture.supplyAsync(
            () -> {
                // Should NOT see first task's correlation ID
                String capturedId = MDC.get(MDC_KEY);
                
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                return capturedId;
            },
            verificationExecutor
        );
        
        String secondCapturedId = secondFuture.get();
        
        // ASSERT: Second task should NOT see first task's correlation ID
        assertNull(secondCapturedId,
            "Second task must not see previous task's correlation ID (validates MDC cleanup)");
        
        assertNotEquals(firstCorrelationId, secondCapturedId,
            "MDC must be cleaned up between task executions to prevent leak");
    }
}
