# ==============================================
# METRICS VERIFICATION SCRIPT (PowerShell)
# ==============================================
# Quick verification script to check if all metrics are properly exposed
# Run this after starting the sync-service
#
# Usage: .\verify-metrics.ps1
# ==============================================

param(
    [string]$ServiceUrl = "http://localhost:8084"
)

$PrometheusEndpoint = "$ServiceUrl/actuator/prometheus"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Sync Service Metrics Verification" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Service URL: $ServiceUrl"
Write-Host "Prometheus Endpoint: $PrometheusEndpoint"
Write-Host ""

# Function to check if service is running
function Test-ServiceHealth {
    Write-Host "🔍 Checking if service is running..." -ForegroundColor Yellow
    try {
        $response = Invoke-WebRequest -Uri "$ServiceUrl/actuator/health" -Method Get -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "✅ Service is UP" -ForegroundColor Green
            return $true
        }
    } catch {
        Write-Host "❌ Service is DOWN or unreachable" -ForegroundColor Red
        Write-Host "   Please start the service first: mvn spring-boot:run" -ForegroundColor Yellow
        return $false
    }
}

# Function to check specific metric
function Test-Metric {
    param(
        [string]$MetricName,
        [string]$Description
    )
    
    Write-Host ""
    Write-Host "🔍 Checking metric: $MetricName" -ForegroundColor Yellow
    
    try {
        $response = Invoke-WebRequest -Uri $PrometheusEndpoint -Method Get -ErrorAction Stop
        $content = $response.Content
        
        $metricLines = $content -split "`n" | Where-Object { $_ -match "^# HELP $MetricName|^$MetricName" }
        
        if ($metricLines) {
            Write-Host "✅ Metric found: $MetricName" -ForegroundColor Green
            $metricLines | ForEach-Object { Write-Host $_ -ForegroundColor Gray }
            return $true
        } else {
            Write-Host "❌ Metric NOT found: $MetricName" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "❌ Error checking metric: $MetricName" -ForegroundColor Red
        Write-Host "   Error: $_" -ForegroundColor Red
        return $false
    }
}

# Function to check all production metrics
function Test-AllMetrics {
    $allOk = $true
    
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "Checking Production Metrics" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    
    if (-not (Test-Metric "sync_job_failure_count" "Number of failed sync jobs")) {
        $allOk = $false
    }
    
    if (-not (Test-Metric "constraint_violation_count" "Number of database constraint violations")) {
        $allOk = $false
    }
    
    if (-not (Test-Metric "parser_warning_count" "Number of timestamp parsing failures")) {
        $allOk = $false
    }
    
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "Checking Existing Metrics" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    
    Test-Metric "sync_jobs_total" "Total number of sync jobs" | Out-Null
    Test-Metric "sync_duration_seconds" "Duration of sync operations" | Out-Null
    
    return $allOk
}

# Function to display current metric values
function Show-MetricValues {
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "Current Metric Values" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    
    try {
        $response = Invoke-WebRequest -Uri $PrometheusEndpoint -Method Get -ErrorAction Stop
        $content = $response.Content
        
        $metricValues = $content -split "`n" | Where-Object { 
            $_ -match "^sync_job_failure_count|^constraint_violation_count|^parser_warning_count" -and 
            $_ -notmatch "^# " 
        }
        
        if ($metricValues) {
            $metricValues | ForEach-Object { Write-Host $_ -ForegroundColor Gray }
        } else {
            Write-Host "No metric values found yet (service may have just started)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "Error retrieving metric values: $_" -ForegroundColor Red
    }
    
    Write-Host ""
}

# Main execution
function Main {
    if (-not (Test-ServiceHealth)) {
        exit 1
    }
    
    if (Test-AllMetrics) {
        Show-MetricValues
        
        Write-Host ""
        Write-Host "==========================================" -ForegroundColor Green
        Write-Host "Verification Complete" -ForegroundColor Green
        Write-Host "==========================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "✅ All production metrics are properly exposed!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Next steps:" -ForegroundColor Yellow
        Write-Host "1. Configure Prometheus to scrape this endpoint"
        Write-Host "2. Deploy alert rules from prometheus-alerts.yml"
        Write-Host "3. Verify alerts in Prometheus UI: http://localhost:9090/alerts"
        Write-Host "4. Test alert firing in staging environment"
        Write-Host ""
        exit 0
    } else {
        Write-Host ""
        Write-Host "==========================================" -ForegroundColor Red
        Write-Host "Verification Failed" -ForegroundColor Red
        Write-Host "==========================================" -ForegroundColor Red
        Write-Host ""
        Write-Host "❌ Some metrics are missing or service is not properly configured" -ForegroundColor Red
        Write-Host ""
        Write-Host "Troubleshooting steps:" -ForegroundColor Yellow
        Write-Host "1. Check if service started successfully: docker-compose logs sync-service"
        Write-Host "2. Verify application.yml has metrics enabled"
        Write-Host "3. Check for errors in SyncMetrics bean initialization"
        Write-Host "4. Review service logs for exceptions"
        Write-Host ""
        exit 1
    }
}

# Run main
Main
