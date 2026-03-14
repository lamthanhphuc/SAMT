# Load Test & Metrics Analysis Guide

## 🎯 Overview

Load test cho Project Config Service với:
- **100 concurrent requests**
- **60 seconds duration**
- **Mixed scenarios**: normal, slow, 4xx, 5xx
- **Real-time metrics monitoring**

## 📋 Prerequisites

1. **Service running**:
   ```powershell
   cd c:\Users\ADMIN\Desktop\Bin\SAMT\project-config-service
   mvn spring-boot:run
   ```

2. **K6 installed** (for load test):
   ```powershell
   # Install via chocolatey
   choco install k6
   
   # Or download from: https://k6.io/docs/getting-started/installation/
   ```

3. **Service dependencies**:
   - PostgreSQL running (for DB)
   - User-Group Service (for gRPC validation)

## 🚀 Running Load Test

### Step 1: Start Metrics Monitor (Terminal 1)

```powershell
cd c:\Users\ADMIN\Desktop\Bin\SAMT\project-config-service
.\monitor-metrics.ps1
```

Bạn sẽ thấy dashboard real-time:
```
📊 Metrics Dashboard - 20:10:15
=============================================

🏥 Health: UP

🔧 Executor (verificationExecutor):
  Active Threads:    45 / 100
  Queued Tasks:      0 (should be 0)
  Pool Size:         100
  Completed Tasks:   1523

🌐 HTTP Connection Pool:
  Active Connections:  42
  Idle Connections:    18
  Pending Requests:    0
  Max Connections:     200

🛡️  Bulkhead - Jira Verification:
  Available Slots:   55 / 100
  In Use:            45

⚡ Circuit Breaker - Jira:
  State:             CLOSED ✅
  Failure Rate:      12.5%
  Successful Calls:  1345
```

### Step 2: Run Load Test (Terminal 2)

```powershell
cd c:\Users\ADMIN\Desktop\Bin\SAMT\project-config-service
k6 run load-test.js
```

**Output example**:
```
scenarios: (100.00%) 1 scenario, 100 max VUs, 1m30s max duration
default: 100 looping VUs for 1m0s

✅ valid-jira: Success (234ms)
✅ valid-jira: Success (189ms)
⚠️  invalid-auth: Validation error (expected)
🔴 slow-upstream: Circuit OPEN
🟡 service-unavailable: Bulkhead FULL
...

📊 LOAD TEST SUMMARY
==========================================

⏱️  Response Time:
  - p50: 245.32ms
  - p95: 6234.56ms
  - p99: 7891.23ms
  - avg: 1523.45ms
  - max: 8234.12ms

✅ Success Rate: 78.45%
❌ Failure Rate: 21.55%
🔴 Circuit Open Rate: 5.23%
🟡 Bulkhead Full Rate: 3.12%

📈 Total Requests: 5234
🚫 Total Errors: 346
👥 Virtual Users: 100
```

## 📊 Metrics Analysis Checklist

### 1. HTTP Connection Pool

**Check**: `httpclient.connections.pending`

✅ **HEALTHY**: `pending = 0` (steady state)
```
Active:   45
Idle:     15
Pending:  0    ← Good!
Max:      200
```

🔴 **BOTTLENECK**: `pending > 0` frequently
```
Active:   100
Idle:     0
Pending:  25   ← BAD! Connection pool exhausted
Max:      200
```

**Action**: Increase `defaultMaxPerRoute` in RestTemplateConfig:
```java
cm.setDefaultMaxPerRoute(200);  // Increase from 100 to 200
```

---

### 2. Bulkhead Saturation

**Check**: `resilience4j.bulkhead.available.concurrent.calls`

✅ **HEALTHY**: Available slots fluctuate (20-80)
```
Available: 45 / 100   ← Good utilization
In Use:    55
```

⚠️ **NEAR CAPACITY**: Available < 10 frequently
```
Available: 5 / 100    ← Close to saturation
In Use:    95
```

🔴 **SATURATED**: Available = 0
```
Available: 0 / 100    ← SATURATED! Requests rejected
In Use:    100
```

**Action**: 
- If legitimate load → increase bulkhead limit
- If attack → keep limit, add rate limiting

---

### 3. Executor Alignment

**Check**: Compare `executor.active` vs `bulkhead available`

✅ **ALIGNED**: 
```
executor.active = 55
bulkhead in use = 55   ← Perfect 1:1 alignment
```

🔴 **MISMATCH**:
```
executor.active = 30
bulkhead in use = 100  ← Logic error!
```

This means bulkhead thinks 100 tasks running, but executor only has 30.
**Action**: Check @Async configuration, thread pool binding.

---

### 4. Circuit Breaker Behavior

**Check**: `resilience4j.circuitbreaker.state`

✅ **CORRECT BEHAVIOR**:
- CLOSED during normal operation
- OPEN after sustained failures (50% for 10+ calls)
- HALF_OPEN for recovery testing

🔴 **TOO SENSITIVE** (opens too early):
```
Failure Rate: 15%
State: OPEN          ← Opened at 15% (threshold 50%)
Calls: 5             ← Only 5 calls (minimumNumberOfCalls: 10)
```

**Action**: Check `minimumNumberOfCalls` and `failureRateThreshold`.

---

### 5. Queue Check (Critical)

**Check**: `executor.queued`

✅ **CORRECT**: Always = 0 (queueCapacity=0)
```
Queued Tasks: 0      ← Correct! Fail-fast working
```

🔴 **CONFIGURATION ERROR**: > 0
```
Queued Tasks: 50     ← ERROR! Queue not disabled
```

**Action**: Verify `executor.setQueueCapacity(0)` in AsyncConfig.

---

## 🎯 Expected Results

### Under Normal Load (100 concurrent)

| Metric | Expected | Issue if |
|--------|----------|----------|
| `executor.active` | 40-80 | = 100 constantly |
| `httpclient.connections.pending` | = 0 | > 0 frequently |
| `bulkhead.available` | 20-80 | = 0 |
| `circuitbreaker.state` | CLOSED (0) | OPEN (1) too often |
| `executor.queued` | = 0 | > 0 ever |

### Under Overload (>100 concurrent)

**Expected behavior**:
1. Bulkhead returns **503 Bulkhead Full** immediately
2. Circuit breaker trips after **50% failure in 50 calls**
3. All subsequent requests fast-fail with **503 Circuit Open**
4. No pending HTTP connections (fail before HTTP call)
5. Queue remains **0** (no buffering)

---

## 🔧 Tuning Recommendations

### If `httpclient.connections.pending > 0` frequently:

**Problem**: Connection pool bottleneck
**Solution**: Increase connection pool size

```java
// RestTemplateConfig.java
cm.setDefaultMaxPerRoute(200);  // ↑ from 100
cm.setMaxTotal(400);            // ↑ from 200
```

### If `bulkhead.available = 0` but `executor.active < 100`:

**Problem**: Bulkhead/Executor mismatch
**Solution**: Check configuration alignment

```yaml
# application.yml
resilience4j:
  bulkhead:
    instances:
      jiraVerification:
        maxConcurrentCalls: 100  # Must match executor max
```

```java
// AsyncConfig.java
executor.setCorePoolSize(100);
executor.setMaxPoolSize(100);    // Must match bulkhead
executor.setQueueCapacity(0);    // Must be 0
```

### If circuit opens too early:

**Problem**: Circuit too sensitive
**Solution**: Adjust thresholds

```yaml
resilience4j:
  circuitbreaker:
    instances:
      jiraVerification:
        minimumNumberOfCalls: 20        # ↑ from 10
        failureRateThreshold: 60        # ↑ from 50
        slowCallDurationThreshold: 7s   # ↑ from 5s
```

---

## 📈 Success Criteria

✅ **PASS**: Load test passes if:
1. ✅ No compilation errors
2. ✅ Service handles 100 concurrent without crash
3. ✅ `httpclient.connections.pending ≈ 0` in steady state
4. ✅ `executor.queued = 0` always (fail-fast working)
5. ✅ Circuit breaker activates only on real failures
6. ✅ Bulkhead rejects when > 100 concurrent
7. ✅ p95 response time < 8s (6s timeout + overhead)

---

## 🐛 Troubleshooting

### Service won't start

```powershell
# Check ports
netstat -ano | findstr :8083

# Kill process
taskkill /PID <PID> /F

# Check logs
tail -f logs/application.log
```

### Metrics not showing

```powershell
# Verify actuator endpoints
curl http://localhost:8083/actuator/health
curl http://localhost:8083/actuator/metrics

# Check application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

### K6 errors

```powershell
# Test endpoint manually
curl -X POST http://localhost:8083/api/project-configs `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer <token>" `
  -d '{"projectName":"test","groupId":1,"jira":{"hostUrl":"https://test.atlassian.net","apiToken":"test","email":"test@test.com"}}'
```

---

## 📊 Prometheus Integration (Optional)

If using Prometheus:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'project-config-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8083']
```

**Useful queries**:
```promql
# Executor active threads
executor_active{name="verificationExecutor"}

# Bulkhead available
resilience4j_bulkhead_available_concurrent_calls{name="jiraVerification"}

# HTTP pending connections
httpclient_connections_pending

# Circuit breaker state
resilience4j_circuitbreaker_state{name="jiraVerification"}
```

---

## ✅ Final Checklist

Before production:

- [ ] Load test passes with 100 concurrent
- [ ] No `httpclient.connections.pending` bottleneck
- [ ] Circuit breaker behaves correctly
- [ ] Bulkhead rejects at capacity
- [ ] Queue always = 0
- [ ] Metrics exported to Prometheus
- [ ] Alerts configured for anomalies
- [ ] Dashboard created (Grafana)

---

## 📝 Notes

- Load test script uses **mock scenarios** - need real upstream services for accurate results
- Monitor actual **upstream response times** to validate timeout configuration
- Adjust bulkhead limits based on **actual throughput requirements**
- Consider **distributed tracing** (Zipkin/Jaeger) for request flow analysis
