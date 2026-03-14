# ==============================================
# UNIFIED ACTIVITY BATCH UPSERT VERIFICATION
# ==============================================
# Purpose: Verify performance & safety after refactoring loop-based UPSERT
# Expected: <400ms for 500 records, <800ms for 1000 records, no deadlocks

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "UNIFIED ACTIVITY BATCH UPSERT VERIFICATION" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

$ErrorActionPreference = "Continue"
$testsPassed = $true

# Test 1: Compile verification
Write-Host "[1/3] Compiling project..." -ForegroundColor Yellow
Push-Location "c:\Users\ADMIN\Desktop\Bin\SAMT\sync-service"

$compileResult = mvn clean compile -q 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ COMPILE FAILED" -ForegroundColor Red
    Write-Host $compileResult -ForegroundColor Red
    $testsPassed = $false
} else {
    Write-Host "✅ COMPILE: PASS" -ForegroundColor Green
}

# Test 2: Run performance & concurrency tests
Write-Host "`n[2/3] Running performance tests..." -ForegroundColor Yellow
Write-Host "Target: 500 records < 400ms, 1000 records < 800ms`n" -ForegroundColor Gray

$testOutput = mvn test -Dtest=UnifiedActivityPerformanceTest -q 2>&1 | Out-String

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ TESTS FAILED" -ForegroundColor Red
    Write-Host $testOutput -ForegroundColor Red
    $testsPassed = $false
} else {
    Write-Host "✅ ALL TESTS: PASS" -ForegroundColor Green
    
    # Extract performance metrics
    $testOutput -split "`n" | Where-Object { 
        $_ -match "✅" -or $_ -match "RECORDS:" -or $_ -match "ms" 
    } | ForEach-Object {
        Write-Host $_ -ForegroundColor Cyan
    }
}

# Test 3: Check spring boot startup (quick smoke test)
Write-Host "`n[3/3] Smoke test: Spring Boot context load..." -ForegroundColor Yellow

$contextTest = mvn test -Dtest=UnifiedActivityPerformanceTest#testConstraintExists -q 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ SPRING CONTEXT: LOAD SUCCESS" -ForegroundColor Green
} else {
    Write-Host "⚠️  SPRING CONTEXT: Issue detected" -ForegroundColor Yellow
    $testsPassed = $false
}

Pop-Location

# Final verdict
Write-Host "`n========================================" -ForegroundColor Cyan
if ($testsPassed) {
    Write-Host "VERDICT: ✅ SAFE FOR PRODUCTION" -ForegroundColor Green
    Write-Host "========================================`n" -ForegroundColor Cyan
    Write-Host "Summary:" -ForegroundColor White
    Write-Host "  - Unique constraint verified" -ForegroundColor White
    Write-Host "  - Performance targets met" -ForegroundColor White
    Write-Host "  - Idempotency confirmed" -ForegroundColor White
    Write-Host "  - No deadlocks detected" -ForegroundColor White
    Write-Host "  - No hanging locks" -ForegroundColor White
    Write-Host "`nRecommendation: Deploy to production" -ForegroundColor Green
    exit 0
} else {
    Write-Host "VERDICT: ❌ NEED FIX" -ForegroundColor Red
    Write-Host "========================================`n" -ForegroundColor Cyan
    Write-Host "Issues detected. Review output above." -ForegroundColor Yellow
    exit 1
}
