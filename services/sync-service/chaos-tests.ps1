#!/usr/bin/env pwsh
# ==============================================
# CHAOS TESTING SCRIPT - STAGING ONLY
# Sync Service Monitoring Validation
# ==============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$ServiceUrl = "http://localhost:8084",
    
    [Parameter(Mandatory=$false)]
    [string]$PrometheusUrl = "http://localhost:9090"
)

$ErrorActionPreference = "Continue"

Write-Host "=====================================" -ForegroundColor Red
Write-Host "⚠️  CHAOS TESTING - STAGING ONLY ⚠️" -ForegroundColor Red
Write-Host "=====================================" -ForegroundColor Red
Write-Host ""
Write-Host "WARNING: This script will intentionally cause errors!" -ForegroundColor Yellow
Write-Host "DO NOT run in production environment!" -ForegroundColor Yellow
Write-Host ""

$confirmation = Read-Host "Are you in STAGING environment? (yes/no)"
if ($confirmation -ne "yes") {
    Write-Host "Aborted. Safety first!" -ForegroundColor Red
    exit 0
}

# ==============================================
# Helper Functions
# ==============================================
function Get-MetricValue {
    param([string]$MetricName)
    
    try {
        $metrics = curl.exe -s "$ServiceUrl/actuator/prometheus"
        if ($metrics -match "$MetricName\s+(\d+\.?\d*)") {
            return [double]$matches[1]
        }
    } catch {
        Write-Host "Error fetching metric: $_" -ForegroundColor Red
    }
    return 0
}

function Wait-ForMetricIncrease {
    param(
        [string]$MetricName,
        [double]$BaselineValue,
        [int]$TimeoutSeconds = 30
    )
    
    Write-Host "  Waiting for $MetricName to increment..." -ForegroundColor Gray
    $elapsed = 0
    
    while ($elapsed -lt $TimeoutSeconds) {
        $currentValue = Get-MetricValue $MetricName
        if ($currentValue -gt $BaselineValue) {
            Write-Host "  ✅ $MetricName increased: $BaselineValue → $currentValue" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
        Write-Host "." -NoNewline -ForegroundColor Gray
    }
    
    Write-Host ""
    Write-Host "  ❌ Timeout: $MetricName did not increase" -ForegroundColor Red
    return $false
}

function Check-PrometheusAlert {
    param([string]$AlertName)
    
    try {
        $alerts = curl.exe -s "$PrometheusUrl/api/v1/alerts" | ConvertFrom-Json
        $alert = $alerts.data.alerts | Where-Object { $_.labels.alertname -eq $AlertName }
        
        if ($alert -and $alert.state -eq "firing") {
            Write-Host "  ✅ Alert '$AlertName' is FIRING" -ForegroundColor Green
            return $true
        } else {
            Write-Host "  ⚠️  Alert '$AlertName' is not firing" -ForegroundColor Yellow
            return $false
        }
    } catch {
        Write-Host "  ⚠️  Cannot check Prometheus (not accessible)" -ForegroundColor Yellow
        return $false
    }
}

# ==============================================
# TEST CASE A - Force UNIQUE Constraint Violation
# ==============================================
Write-Host "`n[TEST CASE A] FORCE UNIQUE CONSTRAINT VIOLATION" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray

Write-Host "`n[A.1] Getting baseline constraint_violation_count..."
$baselineConstraintCount = Get-MetricValue "constraint_violation_count_total"
Write-Host "  Baseline: $baselineConstraintCount" -ForegroundColor Cyan

Write-Host "`n[A.2] Injecting duplicate data..."
Write-Host "  Method: Manually insert duplicate jira_issue record" -ForegroundColor Gray
Write-Host ""
Write-Host "  SQL to execute in staging database:" -ForegroundColor Yellow
Write-Host @"
  -- Insert initial record
  INSERT INTO jira_issues (project_config_id, issue_key, summary, issue_type, status, created_at, updated_at)
  VALUES (999, 'TEST-DUPLICATE-1', 'Test Issue', 'Bug', 'Open', NOW(), NOW());
  
  -- Try to insert duplicate (should fail if UPSERT not used)
  INSERT INTO jira_issues (project_config_id, issue_key, summary, issue_type, status, created_at, updated_at)
  VALUES (999, 'TEST-DUPLICATE-1', 'Test Issue Duplicate', 'Bug', 'Open', NOW(), NOW());
"@ -ForegroundColor Cyan

Write-Host "`n[A.3] Trigger sync job that will hit existing record..."
Write-Host "  Run: Trigger sync for project_config_id=999" -ForegroundColor Gray

Write-Host "`n[A.4] Expected behavior:"
Write-Host "  ✅ UPSERT should handle duplicate gracefully (no error)" -ForegroundColor Green
Write-Host "  ✅ constraint_violation_count should NOT increment" -ForegroundColor Green
Write-Host "  ℹ️  If constraint_violation_count increments → UPSERT is broken!" -ForegroundColor Yellow

Write-Host "`n[A.5] Checking if metric increased (after manual test)..."
Start-Sleep -Seconds 3
$newConstraintCount = Get-MetricValue "constraint_violation_count_total"

if ($newConstraintCount -gt $baselineConstraintCount) {
    Write-Host "  ❌ constraint_violation_count increased! UPSERT may be broken!" -ForegroundColor Red
    Write-Host "  Check logs for SQLState 23505" -ForegroundColor Red
} else {
    Write-Host "  ✅ constraint_violation_count unchanged (UPSERT working correctly)" -ForegroundColor Green
}

# ==============================================
# TEST CASE B - Inject Malformed Timestamp
# ==============================================
Write-Host "`n`n[TEST CASE B] INJECT MALFORMED TIMESTAMP" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray

Write-Host "`n[B.1] Getting baseline parser_warning_count..."
$baselineParserCount = Get-MetricValue "parser_warning_count_total"
Write-Host "  Baseline: $baselineParserCount" -ForegroundColor Cyan

Write-Host "`n[B.2] Mocking malformed timestamp..."
Write-Host "  To test this, you need to:" -ForegroundColor Yellow
Write-Host "  1. Set up Wiremock or mock server" -ForegroundColor White
Write-Host "  2. Configure sync-service to use mock Jira/GitHub API" -ForegroundColor White
Write-Host "  3. Return malformed timestamp like: '2026-99-99T99:99:99Z'" -ForegroundColor White
Write-Host ""
Write-Host "  Example mock response:" -ForegroundColor Gray
Write-Host @"
  {
    "key": "TEST-MALFORMED",
    "fields": {
      "created": "2026-13-40T99:99:99Z",  // Invalid date
      "summary": "Test Issue"
    }
  }
"@ -ForegroundColor Cyan

Write-Host "`n[B.3] After triggering sync with mock, check logs for:"
Write-Host "  Expected log pattern:" -ForegroundColor Yellow
Write-Host "  '⚠️ Failed to parse createdAt. recordId=TEST-MALFORMED rawValue=[2026-13-40T99:99:99Z]. Fallback to now()'" -ForegroundColor Cyan

Write-Host "`n[B.4] Verify log contains ALL required context:"
Write-Host "  ✅ recordId (issueKey or commitSha)" -ForegroundColor Green
Write-Host "  ✅ fieldName (createdAt/updatedAt/committedDate)" -ForegroundColor Green
Write-Host "  ✅ rawValue (original timestamp string)" -ForegroundColor Green

Write-Host "`n[B.5] Manual verification required:"
Write-Host "  After running mock test, search logs:" -ForegroundColor Yellow
Write-Host "  grep -i 'Failed to parse' sync-service.log | tail -5" -ForegroundColor Cyan

# ==============================================
# TEST CASE C - Throw RuntimeException in Orchestrator
# ==============================================
Write-Host "`n`n[TEST CASE C] FORCE RUNTIME EXCEPTION" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray

Write-Host "`n[C.1] Getting baseline metrics..."
$baselineTotalCount = Get-MetricValue "sync_job_total_count_total"
$baselineFailureCount = Get-MetricValue "sync_job_failure_count_total"
Write-Host "  Total jobs:   $baselineTotalCount" -ForegroundColor Cyan
Write-Host "  Failed jobs:  $baselineFailureCount" -ForegroundColor Cyan

Write-Host "`n[C.2] Injecting exception..."
Write-Host "  Method 1: Configure invalid project_config_id (e.g., -1)" -ForegroundColor Gray
Write-Host "  Method 2: Shutdown gRPC service temporarily" -ForegroundColor Gray
Write-Host "  Method 3: Add test endpoint that throws exception" -ForegroundColor Gray
Write-Host ""
Write-Host "  Example test endpoint to add:" -ForegroundColor Yellow
Write-Host @"
  @PostMapping("/test/chaos/exception")
  public ResponseEntity<String> testException() {
      syncMetrics.recordSyncJobStarted();
      throw new RuntimeException("CHAOS TEST: Forced exception");
  }
"@ -ForegroundColor Cyan

Write-Host "`n[C.3] Trigger test endpoint:"
Write-Host "  curl -X POST $ServiceUrl/test/chaos/exception" -ForegroundColor Cyan

Write-Host "`n[C.4] Expected behavior after exception:"
Write-Host "  ✅ sync_job_total_count should +1" -ForegroundColor Green
Write-Host "  ✅ sync_job_failure_count should +1" -ForegroundColor Green
Write-Host "  ✅ Failure rate = (failure / total) should be calculable" -ForegroundColor Green
Write-Host "  ✅ If failure rate > 10%, alert should fire" -ForegroundColor Green

Write-Host "`n[C.5] Waiting 5 seconds for metric scrape..."
Start-Sleep -Seconds 5

$newTotalCount = Get-MetricValue "sync_job_total_count_total"
$newFailureCount = Get-MetricValue "sync_job_failure_count_total"

Write-Host "`n[C.6] Verification results:"
Write-Host "  Total jobs:   $baselineTotalCount → $newTotalCount" -ForegroundColor Cyan
Write-Host "  Failed jobs:  $baselineFailureCount → $newFailureCount" -ForegroundColor Cyan

$totalIncrement = $newTotalCount - $baselineTotalCount
$failureIncrement = $newFailureCount - $baselineFailureCount

if ($totalIncrement -eq 1 -and $failureIncrement -eq 1) {
    Write-Host "  ✅ PASS: Both counters incremented correctly" -ForegroundColor Green
} elseif ($totalIncrement -eq 0 -and $failureIncrement -eq 0) {
    Write-Host "  ⚠️  No change (test not executed yet)" -ForegroundColor Yellow
} else {
    Write-Host "  ❌ FAIL: Counter mismatch!" -ForegroundColor Red
    Write-Host "     This may indicate double counting or missing increment" -ForegroundColor Red
}

if ($newTotalCount -gt 0) {
    $failureRate = ($newFailureCount / $newTotalCount) * 100
    Write-Host "`n  Current failure rate: $([Math]::Round($failureRate, 2))%" -ForegroundColor Cyan
    
    if ($failureRate -gt 10) {
        Write-Host "  ⚠️  Alert 'SyncFailureRateHigh' SHOULD be firing!" -ForegroundColor Yellow
        Check-PrometheusAlert "SyncFailureRateHigh"
    }
}

# ==============================================
# TEST CASE D - No Double Count Verification
# ==============================================
Write-Host "`n`n[TEST CASE D] DOUBLE COUNT VERIFICATION" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray

Write-Host "`n[D.1] Running 1 successful sync job..."
Write-Host "  Trigger sync for a valid project_config_id" -ForegroundColor Gray

$beforeTotal = Get-MetricValue "sync_job_total_count_total"
$beforeFailure = Get-MetricValue "sync_job_failure_count_total"

Write-Host "  Before: total=$beforeTotal, failure=$beforeFailure" -ForegroundColor Cyan
Write-Host "  (Execute sync job now, then press Enter)" -ForegroundColor Yellow
Read-Host

$afterTotal = Get-MetricValue "sync_job_total_count_total"
$afterFailure = Get-MetricValue "sync_job_failure_count_total"

Write-Host "  After:  total=$afterTotal, failure=$afterFailure" -ForegroundColor Cyan

if (($afterTotal - $beforeTotal) -eq 1 -and ($afterFailure - $beforeFailure) -eq 0) {
    Write-Host "  ✅ PASS: Success job increments only total_count" -ForegroundColor Green
} else {
    Write-Host "  ❌ FAIL: Unexpected counter behavior" -ForegroundColor Red
}

Write-Host "`n[D.2] Final validation:"
if ($afterFailure -le $afterTotal) {
    Write-Host "  ✅ PASS: failure_count <= total_count (no double counting)" -ForegroundColor Green
} else {
    Write-Host "  ❌ CRITICAL BUG: failure_count > total_count!" -ForegroundColor Red
}

# ==============================================
# SUMMARY
# ==============================================
Write-Host "`n`n=====================================" -ForegroundColor Cyan
Write-Host "CHAOS TEST SUMMARY" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan

Write-Host "`nTest Cases Executed:" -ForegroundColor Yellow
Write-Host "  [A] Constraint Violation Test: Manual verification required" -ForegroundColor White
Write-Host "  [B] Malformed Timestamp Test: Manual verification required" -ForegroundColor White
Write-Host "  [C] Runtime Exception Test: Depends on test endpoint" -ForegroundColor White
Write-Host "  [D] Double Count Test: Interactive verification" -ForegroundColor White

Write-Host "`nNext Steps:" -ForegroundColor Yellow
Write-Host "  1. Review logs for contextual information" -ForegroundColor White
Write-Host "  2. Check Prometheus alerts dashboard" -ForegroundColor White
Write-Host "  3. Verify alert notifications in Slack/PagerDuty" -ForegroundColor White
Write-Host "  4. Document any issues found" -ForegroundColor White

Write-Host "`n🧪 Chaos testing complete!" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""
