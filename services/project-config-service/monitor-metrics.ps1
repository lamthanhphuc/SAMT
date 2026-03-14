# PowerShell script to monitor metrics during load test
# Run this in a separate terminal while load test is running

$BASE_URL = "http://localhost:8083"

Write-Host "🔍 Monitoring Project Config Service Metrics" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

function Get-MetricValue {
    param($metricName)
    try {
        $response = Invoke-RestMethod -Uri "$BASE_URL/actuator/metrics/$metricName" -Method Get
        return $response.measurements[0].value
    } catch {
        return "N/A"
    }
}

function Get-HealthStatus {
    try {
        $response = Invoke-RestMethod -Uri "$BASE_URL/actuator/health" -Method Get
        return $response.status
    } catch {
        return "DOWN"
    }
}

# Main monitoring loop
while ($true) {
    Clear-Host
    
    $timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "📊 Metrics Dashboard - $timestamp" -ForegroundColor Green
    Write-Host "=============================================" -ForegroundColor Green
    Write-Host ""
    
    # Health
    $health = Get-HealthStatus
    $healthColor = if ($health -eq "UP") { "Green" } else { "Red" }
    Write-Host "🏥 Health: " -NoNewline
    Write-Host $health -ForegroundColor $healthColor
    Write-Host ""
    
    # Executor metrics
    Write-Host "🔧 Executor (verificationExecutor):" -ForegroundColor Yellow
    $executorActive = Get-MetricValue "executor.active"
    $executorQueued = Get-MetricValue "executor.queued"
    $executorPoolSize = Get-MetricValue "executor.pool.size"
    $executorCompleted = Get-MetricValue "executor.completed"
    
    Write-Host "  Active Threads:    $executorActive / 100"
    Write-Host "  Queued Tasks:      $executorQueued (should be 0)"
    Write-Host "  Pool Size:         $executorPoolSize"
    Write-Host "  Completed Tasks:   $executorCompleted"
    
    # Color warning if approaching limit
    if ($executorActive -is [int] -and $executorActive -gt 90) {
        Write-Host "  ⚠️  WARNING: Near capacity!" -ForegroundColor Red
    }
    Write-Host ""
    
    # HTTP Connection Pool
    Write-Host "🌐 HTTP Connection Pool:" -ForegroundColor Yellow
    $httpActive = Get-MetricValue "httpclient.connections.active"
    $httpIdle = Get-MetricValue "httpclient.connections.idle"
    $httpPending = Get-MetricValue "httpclient.connections.pending"
    $httpMax = Get-MetricValue "httpclient.connections.max"
    
    Write-Host "  Active Connections:  $httpActive"
    Write-Host "  Idle Connections:    $httpIdle"
    Write-Host "  Pending Requests:    $httpPending"
    Write-Host "  Max Connections:     $httpMax"
    
    if ($httpPending -is [int] -and $httpPending -gt 0) {
        Write-Host "  🔴 BOTTLENECK: Pending requests detected!" -ForegroundColor Red
        Write-Host "     → Consider increasing defaultMaxPerRoute" -ForegroundColor Red
    }
    Write-Host ""
    
    # Bulkhead (Jira)
    Write-Host "🛡️  Bulkhead - Jira Verification:" -ForegroundColor Yellow
    $bulkheadAvailable = Get-MetricValue "resilience4j.bulkhead.available.concurrent.calls?tag=name:jiraVerification"
    $bulkheadMax = Get-MetricValue "resilience4j.bulkhead.max.allowed.concurrent.calls?tag=name:jiraVerification"
    
    if ($bulkheadAvailable -ne "N/A" -and $bulkheadMax -ne "N/A") {
        $used = $bulkheadMax - $bulkheadAvailable
        Write-Host "  Available Slots:   $bulkheadAvailable / $bulkheadMax"
        Write-Host "  In Use:            $used"
        
        if ($bulkheadAvailable -eq 0) {
            Write-Host "  🔴 SATURATED: No available slots!" -ForegroundColor Red
        }
    } else {
        Write-Host "  Available Slots:   $bulkheadAvailable"
    }
    Write-Host ""
    
    # Bulkhead (GitHub)
    Write-Host "🛡️  Bulkhead - GitHub Verification:" -ForegroundColor Yellow
    $bulkheadAvailableGH = Get-MetricValue "resilience4j.bulkhead.available.concurrent.calls?tag=name:githubVerification"
    $bulkheadMaxGH = Get-MetricValue "resilience4j.bulkhead.max.allowed.concurrent.calls?tag=name:githubVerification"
    
    if ($bulkheadAvailableGH -ne "N/A" -and $bulkheadMaxGH -ne "N/A") {
        $usedGH = $bulkheadMaxGH - $bulkheadAvailableGH
        Write-Host "  Available Slots:   $bulkheadAvailableGH / $bulkheadMaxGH"
        Write-Host "  In Use:            $usedGH"
    } else {
        Write-Host "  Available Slots:   $bulkheadAvailableGH"
    }
    Write-Host ""
    
    # Circuit Breaker - Jira
    Write-Host "⚡ Circuit Breaker - Jira:" -ForegroundColor Yellow
    $cbState = Get-MetricValue "resilience4j.circuitbreaker.state?tag=name:jiraVerification"
    $cbFailureRate = Get-MetricValue "resilience4j.circuitbreaker.failure.rate?tag=name:jiraVerification"
    $cbCalls = Get-MetricValue "resilience4j.circuitbreaker.calls?tag=name:jiraVerification,kind:successful"
    
    $stateColor = switch ($cbState) {
        0 { "Green" }   # CLOSED
        1 { "Red" }     # OPEN
        2 { "Yellow" }  # HALF_OPEN
        default { "White" }
    }
    $stateText = switch ($cbState) {
        0 { "CLOSED ✅" }
        1 { "OPEN 🔴" }
        2 { "HALF_OPEN 🟡" }
        default { $cbState }
    }
    
    Write-Host "  State:             " -NoNewline
    Write-Host $stateText -ForegroundColor $stateColor
    Write-Host "  Failure Rate:      $cbFailureRate%"
    Write-Host "  Successful Calls:  $cbCalls"
    Write-Host ""
    
    # Circuit Breaker - GitHub
    Write-Host "⚡ Circuit Breaker - GitHub:" -ForegroundColor Yellow
    $cbStateGH = Get-MetricValue "resilience4j.circuitbreaker.state?tag=name:githubVerification"
    $cbFailureRateGH = Get-MetricValue "resilience4j.circuitbreaker.failure.rate?tag=name:githubVerification"
    
    $stateColorGH = switch ($cbStateGH) {
        0 { "Green" }
        1 { "Red" }
        2 { "Yellow" }
        default { "White" }
    }
    $stateTextGH = switch ($cbStateGH) {
        0 { "CLOSED ✅" }
        1 { "OPEN 🔴" }
        2 { "HALF_OPEN 🟡" }
        default { $cbStateGH }
    }
    
    Write-Host "  State:             " -NoNewline
    Write-Host $stateTextGH -ForegroundColor $stateColorGH
    Write-Host "  Failure Rate:      $cbFailureRateGH%"
    Write-Host ""
    
    Write-Host "=============================================" -ForegroundColor Green
    Write-Host "Press Ctrl+C to stop monitoring" -ForegroundColor Gray
    
    Start-Sleep -Seconds 2
}
