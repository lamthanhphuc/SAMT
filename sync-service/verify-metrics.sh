#!/usr/bin/env bash
# ==============================================
# METRICS VERIFICATION SCRIPT
# ==============================================
# Quick verification script to check if all metrics are properly exposed
# Run this after starting the sync-service
#
# Usage: ./verify-metrics.sh
# Or on Windows: bash verify-metrics.sh
# ==============================================

set -e

# Configuration
SERVICE_URL="${SYNC_SERVICE_URL:-http://localhost:8084}"
PROMETHEUS_ENDPOINT="${SERVICE_URL}/actuator/prometheus"

echo "=========================================="
echo "Sync Service Metrics Verification"
echo "=========================================="
echo ""
echo "Service URL: ${SERVICE_URL}"
echo "Prometheus Endpoint: ${PROMETHEUS_ENDPOINT}"
echo ""

# Function to check if service is running
check_service() {
    echo "🔍 Checking if service is running..."
    if curl -s -f "${SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
        echo "✅ Service is UP"
        return 0
    else
        echo "❌ Service is DOWN or unreachable"
        echo "   Please start the service first: mvn spring-boot:run"
        exit 1
    fi
}

# Function to check specific metric
check_metric() {
    local metric_name=$1
    local description=$2
    
    echo ""
    echo "🔍 Checking metric: ${metric_name}"
    
    local result=$(curl -s "${PROMETHEUS_ENDPOINT}" | grep -E "^# HELP ${metric_name}|^${metric_name}")
    
    if [ -n "$result" ]; then
        echo "✅ Metric found: ${metric_name}"
        echo "${result}"
    else
        echo "❌ Metric NOT found: ${metric_name}"
        return 1
    fi
}

# Function to check all production metrics
check_all_metrics() {
    local all_ok=true
    
    echo ""
    echo "=========================================="
    echo "Checking Production Metrics"
    echo "=========================================="
    
    if ! check_metric "sync_job_failure_count" "Number of failed sync jobs"; then
        all_ok=false
    fi
    
    if ! check_metric "constraint_violation_count" "Number of database constraint violations"; then
        all_ok=false
    fi
    
    if ! check_metric "parser_warning_count" "Number of timestamp parsing failures"; then
        all_ok=false
    fi
    
    echo ""
    echo "=========================================="
    echo "Checking Existing Metrics"
    echo "=========================================="
    
    check_metric "sync_jobs_total" "Total number of sync jobs" || true
    check_metric "sync_duration_seconds" "Duration of sync operations" || true
    
    echo ""
    if [ "$all_ok" = true ]; then
        echo "✅ ALL PRODUCTION METRICS VERIFIED"
        return 0
    else
        echo "❌ SOME METRICS ARE MISSING"
        return 1
    fi
}

# Function to display current metric values
show_metric_values() {
    echo ""
    echo "=========================================="
    echo "Current Metric Values"
    echo "=========================================="
    
    curl -s "${PROMETHEUS_ENDPOINT}" | grep -E "^sync_job_failure_count|^constraint_violation_count|^parser_warning_count" | grep -v "^# "
    
    echo ""
}

# Main execution
main() {
    check_service
    
    if check_all_metrics; then
        show_metric_values
        
        echo ""
        echo "=========================================="
        echo "Verification Complete"
        echo "=========================================="
        echo ""
        echo "✅ All production metrics are properly exposed!"
        echo ""
        echo "Next steps:"
        echo "1. Configure Prometheus to scrape this endpoint"
        echo "2. Deploy alert rules from prometheus-alerts.yml"
        echo "3. Verify alerts in Prometheus UI: http://localhost:9090/alerts"
        echo "4. Test alert firing in staging environment"
        echo ""
        exit 0
    else
        echo ""
        echo "=========================================="
        echo "Verification Failed"
        echo "=========================================="
        echo ""
        echo "❌ Some metrics are missing or service is not properly configured"
        echo ""
        echo "Troubleshooting steps:"
        echo "1. Check if service started successfully: docker-compose logs sync-service"
        echo "2. Verify application.yml has metrics enabled"
        echo "3. Check for errors in SyncMetrics bean initialization"
        echo "4. Review service logs for exceptions"
        echo ""
        exit 1
    fi
}

# Run main
main
