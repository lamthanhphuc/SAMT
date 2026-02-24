/**
 * K6 Load Test for Project Config Service - Verification API
 * 
 * Test Configuration:
 * - 100 concurrent virtual users
 * - 60 seconds duration
 * - Test verification endpoint under various conditions
 * 
 * Test Scenarios:
 * 1. Normal response (fast)
 * 2. Upstream delay 5-7s (slow response)
 * 3. Upstream 503 (service failure)
 * 
 * Metrics to Monitor:
 * - executor.active
 * - resilience4j.bulkhead.available.concurrent.calls
 * - httpclient.connections.pending
 * - resilience4j.circuitbreaker.state
 * - resilience4j.circuitbreaker.failure.rate
 * 
 * Run:
 * k6 run load-test.js
 * 
 * Monitor metrics in parallel:
 * watch -n 1 'curl -s http://localhost:8083/actuator/metrics/executor.active | jq'
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const successRate = new Rate('verification_success');
const failureRate = new Rate('verification_failure');
const circuitOpenRate = new Rate('circuit_open');
const bulkheadFullRate = new Rate('bulkhead_full');
const responseTime = new Trend('verification_response_time');
const errorCounter = new Counter('errors_total');

// Test configuration
export const options = {
    stages: [
        { duration: '10s', target: 50 },   // Ramp up to 50 users
        { duration: '10s', target: 100 },  // Ramp up to 100 users
        { duration: '30s', target: 100 },  // Stay at 100 users
        { duration: '10s', target: 0 },    // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<8000'],  // 95% requests < 8s (6s timeout + buffer)
        verification_success: ['rate>0.5'], // At least 50% success
        errors_total: ['count<1000'],       // Max 1000 errors
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const JWT_TOKEN = __ENV.JWT_TOKEN || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwibmFtZSI6IlRlc3QgVXNlciIsInJvbGUiOiJBRE1JTiJ9.test';

// Test data - Mix of scenarios
const testConfigs = [
    // Scenario 1: Valid Jira (should succeed fast)
    {
        name: 'valid-jira',
        weight: 50,
        payload: {
            projectName: 'LoadTest-Valid',
            groupId: 1,
            jira: {
                hostUrl: 'https://jira.atlassian.com',
                apiToken: 'valid-token-simulation',
                email: 'test@example.com'
            }
        }
    },
    // Scenario 2: Invalid auth (4xx - should not trigger circuit)
    {
        name: 'invalid-auth',
        weight: 20,
        payload: {
            projectName: 'LoadTest-Invalid',
            groupId: 2,
            jira: {
                hostUrl: 'https://jira.atlassian.com',
                apiToken: 'invalid-token',
                email: 'test@example.com'
            }
        }
    },
    // Scenario 3: Timeout simulation (should trigger circuit after threshold)
    {
        name: 'slow-upstream',
        weight: 20,
        payload: {
            projectName: 'LoadTest-Slow',
            groupId: 3,
            github: {
                repoUrl: 'https://api.github.com/repos/octocat/Hello-World',
                accessToken: 'slow-simulation'
            }
        }
    },
    // Scenario 4: Service unavailable (5xx - should trigger circuit)
    {
        name: 'service-unavailable',
        weight: 10,
        payload: {
            projectName: 'LoadTest-503',
            groupId: 4,
            jira: {
                hostUrl: 'https://api.github.com/repos/nonexistent/repo',
                apiToken: '503-simulation',
                email: 'test@example.com'
            }
        }
    }
];

// Select test config based on weight
function selectTestConfig() {
    const totalWeight = testConfigs.reduce((sum, config) => sum + config.weight, 0);
    let random = Math.random() * totalWeight;
    
    for (const config of testConfigs) {
        random -= config.weight;
        if (random <= 0) {
            return config;
        }
    }
    return testConfigs[0];
}

export default function() {
    const config = selectTestConfig();
    
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${JWT_TOKEN}`,
        },
        timeout: '10s', // K6 timeout (higher than service timeout)
    };
    
    const startTime = Date.now();
    const response = http.post(
        `${BASE_URL}/api/project-configs`,
        JSON.stringify(config.payload),
        params
    );
    const duration = Date.now() - startTime;
    
    // Record metrics
    responseTime.add(duration);
    
    // Check response
    const success = check(response, {
        'status is 201 or expected error': (r) => r.status === 201 || r.status === 400 || r.status === 503,
        'response has body': (r) => r.body && r.body.length > 0,
    });
    
    if (response.status === 201) {
        successRate.add(1);
        console.log(`✅ ${config.name}: Success (${duration}ms)`);
    } else if (response.status === 400) {
        successRate.add(1);  // Expected validation error
        console.log(`⚠️  ${config.name}: Validation error (expected)`);
    } else if (response.status === 503) {
        failureRate.add(1);
        if (response.body && response.body.includes('circuit breaker')) {
            circuitOpenRate.add(1);
            console.log(`🔴 ${config.name}: Circuit OPEN`);
        } else if (response.body && response.body.includes('bulkhead')) {
            bulkheadFullRate.add(1);
            console.log(`🟡 ${config.name}: Bulkhead FULL`);
        } else {
            console.log(`🔴 ${config.name}: Service unavailable`);
        }
    } else {
        failureRate.add(1);
        errorCounter.add(1);
        console.log(`❌ ${config.name}: Unexpected status ${response.status}`);
    }
    
    // Small think time between requests
    sleep(0.1);
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'load-test-results.json': JSON.stringify(data),
    };
}

function textSummary(data, options) {
    const indent = options.indent || '';
    let output = '\n' + indent + '📊 LOAD TEST SUMMARY\n';
    output += indent + '==========================================\n\n';
    
    // HTTP metrics
    const httpMetrics = data.metrics.http_req_duration;
    if (httpMetrics) {
        output += indent + '⏱️  Response Time:\n';
        output += indent + `  - p50: ${httpMetrics.values.p50.toFixed(2)}ms\n`;
        output += indent + `  - p95: ${httpMetrics.values.p95.toFixed(2)}ms\n`;
        output += indent + `  - p99: ${httpMetrics.values.p99.toFixed(2)}ms\n`;
        output += indent + `  - avg: ${httpMetrics.values.avg.toFixed(2)}ms\n`;
        output += indent + `  - max: ${httpMetrics.values.max.toFixed(2)}ms\n\n`;
    }
    
    // Success/Failure rates
    output += indent + '✅ Success Rate: ' + 
        (data.metrics.verification_success.values.rate * 100).toFixed(2) + '%\n';
    output += indent + '❌ Failure Rate: ' + 
        (data.metrics.verification_failure.values.rate * 100).toFixed(2) + '%\n';
    output += indent + '🔴 Circuit Open Rate: ' + 
        (data.metrics.circuit_open.values.rate * 100).toFixed(2) + '%\n';
    output += indent + '🟡 Bulkhead Full Rate: ' + 
        (data.metrics.bulkhead_full.values.rate * 100).toFixed(2) + '%\n\n';
    
    // Total stats
    output += indent + '📈 Total Requests: ' + data.metrics.http_reqs.values.count + '\n';
    output += indent + '🚫 Total Errors: ' + data.metrics.errors_total.values.count + '\n';
    output += indent + '👥 Virtual Users: ' + Math.max(...data.root_group.checks.map(c => c.passes + c.fails)) + '\n';
    
    output += indent + '\n==========================================\n';
    
    return output;
}
