package com.samt.projectconfig.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * Metrics configuration for production observability.
 * 
 * Binds custom metrics to Micrometer registry:
 * 1. Executor thread pool metrics (verificationExecutor)
 * 2. HTTP connection pool metrics (Apache HttpClient 5)
 * 3. Resilience4j metrics (auto-configured when resilience4j.metrics.enabled=true)
 * 
 * Metrics Exposed:
 * 
 * Executor Metrics:
 * - executor.active (gauge): Active thread count
 * - executor.completed (counter): Total completed tasks
 * - executor.queued (gauge): Queued tasks (0 for us)
 * - executor.pool.size (gauge): Current pool size
 * - executor.pool.core (gauge): Core pool size (100)
 * - executor.pool.max (gauge): Max pool size (100)
 * 
 * HTTP Pool Metrics:
 * - httpclient.connections.active (gauge): Active connections
 * - httpclient.connections.idle (gauge): Idle connections in pool
 * - httpclient.connections.pending (gauge): Pending connection requests
 * - httpclient.connections.max (gauge): Max total connections (200)
 * - httpclient.connections.max.per.route (gauge): Max per route (100)
 * 
 * Resilience4j Metrics (auto):
 * - resilience4j.bulkhead.available.concurrent.calls
 * - resilience4j.bulkhead.max.allowed.concurrent.calls
 * - resilience4j.circuitbreaker.state
 * - resilience4j.circuitbreaker.calls
 * - resilience4j.circuitbreaker.failure.rate
 * 
 * @author Production Team
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MetricsConfig {
    
    private final MeterRegistry meterRegistry;
    private final Executor verificationExecutor;
    private final PoolingHttpClientConnectionManager poolingConnectionManager;
    
    /**
     * Bind executor and HTTP client metrics after bean initialization.
     */
    @PostConstruct
    public void bindCustomMetrics() {
        bindExecutorMetrics();
        bindHttpClientMetrics();
        log.info("✅ Custom metrics bound successfully");
    }
    
    /**
     * Bind verificationExecutor metrics to Micrometer.
     * 
     * Exposes:
     * - executor.active: Active threads (0-100)
     * - executor.completed: Total completed verification tasks
     * - executor.queued: Queued tasks (always 0 due to queueCapacity=0)
     * - executor.pool.size: Current pool size
     * - executor.pool.core: Core pool size (100)
     * - executor.pool.max: Max pool size (100)
     */
    private void bindExecutorMetrics() {
        if (verificationExecutor instanceof ThreadPoolTaskExecutor executor) {
            // Bind using Micrometer's ExecutorServiceMetrics with proper Tag syntax
            ExecutorServiceMetrics.monitor(
                meterRegistry,
                executor.getThreadPoolExecutor(),
                "verificationExecutor",
                Arrays.asList(
                    Tag.of("type", "async-verification")
                )
            );
            
            log.info("✅ Executor metrics bound: verificationExecutor (core={}, max={}, queue={})",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());
        } else {
            log.warn("⚠️ verificationExecutor is not ThreadPoolTaskExecutor, cannot bind metrics");
        }
    }
    
    /**
     * Bind HTTP connection pool metrics to Micrometer.
     * 
     * Exposes:
     * - httpclient.connections.active: Currently active (leased) connections
     * - httpclient.connections.idle: Idle connections in pool (available for reuse)
     * - httpclient.connections.pending: Pending connection requests (waiting for available connection)
     * - httpclient.connections.max: Max total connections (200)
     * - httpclient.connections.max.per.route: Max connections per route (100)
     * 
     * Critical for detecting:
     * - Connection pool exhaustion (active ≈ max)
     * - Connection leaks (idle not returning to pool)
     * - Pending request buildup (pending > 0 = bottleneck)
     */
    private void bindHttpClientMetrics() {
        // Active connections (leased out)
        meterRegistry.gauge(
            "httpclient.connections.active",
            poolingConnectionManager,
            cm -> cm.getTotalStats().getLeased()
        );
        
        // Idle connections (available in pool)
        meterRegistry.gauge(
            "httpclient.connections.idle",
            poolingConnectionManager,
            cm -> cm.getTotalStats().getAvailable()
        );
        
        // Pending connection requests
        meterRegistry.gauge(
            "httpclient.connections.pending",
            poolingConnectionManager,
            cm -> cm.getTotalStats().getPending()
        );
        
        // Max total connections
        meterRegistry.gauge(
            "httpclient.connections.max",
            poolingConnectionManager,
            cm -> cm.getTotalStats().getMax()
        );
        
        // Max connections per route
        meterRegistry.gauge(
            "httpclient.connections.max.per.route",
            poolingConnectionManager,
            cm -> cm.getDefaultMaxPerRoute()
        );
        
        log.info("✅ HTTP client pool metrics bound: maxTotal={}, maxPerRoute={}",
            poolingConnectionManager.getTotalStats().getMax(),
            poolingConnectionManager.getDefaultMaxPerRoute());
    }
}
