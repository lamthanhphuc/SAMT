-- ==============================================
-- VERIFICATION QUERIES FOR V7 MIGRATION
-- ==============================================
-- Run these queries after migration to confirm schema correctness
-- Expected result: All checks should return TRUE or expected counts
-- ==============================================

-- ==============================================
-- CHECK 1: Verify all required columns exist
-- ==============================================
SELECT 
    EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'sync_jobs' 
          AND column_name = 'records_fetched'
    ) AS records_fetched_exists,
    EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'sync_jobs' 
          AND column_name = 'records_saved'
    ) AS records_saved_exists,
    EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'sync_jobs' 
          AND column_name = 'correlation_id'
    ) AS correlation_id_exists,
    EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'sync_jobs' 
          AND column_name = 'items_processed'
    ) AS items_processed_preserved;
-- Expected: All = TRUE

-- ==============================================
-- CHECK 2: Verify data migration correctness
-- ==============================================
-- Compare old and new columns to ensure data copied correctly
SELECT 
    COUNT(*) AS total_rows,
    COUNT(*) FILTER (WHERE records_fetched = items_processed) AS fetched_matches,
    COUNT(*) FILTER (WHERE records_saved = items_processed) AS saved_matches,
    COUNT(*) FILTER (WHERE records_fetched != items_processed) AS fetched_mismatch,
    COUNT(*) FILTER (WHERE items_processed IS NOT NULL AND items_processed > 0) AS rows_with_data
FROM sync_jobs;
-- Expected: fetched_matches = saved_matches = rows_with_data

-- ==============================================
-- CHECK 3: Verify column constraints and defaults
-- ==============================================
SELECT 
    column_name,
    data_type,
    column_default,
    is_nullable,
    character_maximum_length
FROM information_schema.columns 
WHERE table_name = 'sync_jobs' 
  AND column_name IN ('records_fetched', 'records_saved', 'correlation_id', 'items_processed')
ORDER BY column_name;
-- Expected:
-- records_fetched   | integer | 0    | NO  | NULL
-- records_saved     | integer | 0    | NO  | NULL  
-- correlation_id    | varchar | NULL | YES | 100
-- items_processed   | integer | 0    | YES | NULL

-- ==============================================
-- CHECK 4: Verify indexes created
-- ==============================================
SELECT 
    indexname,
    indexdef
FROM pg_indexes 
WHERE tablename = 'sync_jobs' 
  AND indexname = 'idx_sync_jobs_correlation_id';
-- Expected: 1 row returned with partial index definition

-- ==============================================
-- CHECK 5: Verify no data loss during migration
-- ==============================================
-- Check if any rows have data in items_processed but not in new columns
SELECT 
    id,
    project_config_id,
    job_type,
    status,
    items_processed,
    records_fetched,
    records_saved,
    started_at
FROM sync_jobs
WHERE items_processed IS NOT NULL 
  AND items_processed > 0
  AND (records_fetched = 0 OR records_saved = 0)
ORDER BY id DESC
LIMIT 10;
-- Expected: 0 rows (all data should be migrated)

-- ==============================================
-- CHECK 6: Sample data inspection
-- ==============================================
-- Visual inspection of migrated data
SELECT 
    id,
    project_config_id,
    job_type,
    status,
    items_processed AS old_column,
    records_fetched AS new_fetched,
    records_saved AS new_saved,
    correlation_id,
    started_at,
    completed_at
FROM sync_jobs
WHERE items_processed IS NOT NULL
ORDER BY id DESC
LIMIT 20;
-- Expected: old_column = new_fetched = new_saved for all rows

-- ==============================================
-- CHECK 7: Verify Flyway migration history
-- ==============================================
SELECT 
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_on,
    execution_time,
    success
FROM flyway_schema_history
WHERE version IN ('1', '6', '7')
ORDER BY installed_rank;
-- Expected: V7 shows success = TRUE, execution_time < 5000ms

-- ==============================================
-- CHECK 8: Test write operation (optional)
-- ==============================================
-- Simulate new SyncJob creation to verify Hibernate can write to new columns
-- Run this in TRANSACTION and ROLLBACK if testing
DO $$
DECLARE
    test_job_id BIGINT;
BEGIN
    -- Insert test record
    INSERT INTO sync_jobs (
        project_config_id,
        job_type,
        status,
        started_at,
        created_at,
        updated_at,
        records_fetched,
        records_saved,
        correlation_id
    ) VALUES (
        999,
        'JIRA_ISSUES',
        'RUNNING',
        NOW(),
        NOW(),
        NOW(),
        50,
        45,
        'TEST-verify'
    ) RETURNING id INTO test_job_id;
    
    RAISE NOTICE 'Test job created with id=%', test_job_id;
    
    -- Verify insert
    IF EXISTS (
        SELECT 1 FROM sync_jobs 
        WHERE id = test_job_id 
          AND records_fetched = 50 
          AND records_saved = 45
          AND correlation_id = 'TEST-verify'
    ) THEN
        RAISE NOTICE '✅ Write verification PASSED';
    ELSE
        RAISE EXCEPTION '❌ Write verification FAILED';
    END IF;
    
    -- Cleanup test data
    DELETE FROM sync_jobs WHERE id = test_job_id;
    RAISE NOTICE '✅ Test data cleaned up';
END $$;
-- Expected: "Write verification PASSED" message

-- ==============================================
-- SUMMARY VERIFICATION QUERY
-- ==============================================
-- Single query to check migration health
SELECT 
    'V7 Migration Status' AS check_type,
    CASE 
        WHEN records_fetched_col AND records_saved_col AND correlation_id_col AND items_preserved
        THEN '✅ PASS'
        ELSE '❌ FAIL'
    END AS status,
    jsonb_build_object(
        'records_fetched_exists', records_fetched_col,
        'records_saved_exists', records_saved_col,
        'correlation_id_exists', correlation_id_col,
        'items_processed_preserved', items_preserved,
        'data_migrated_correctly', data_ok,
        'index_created', idx_exists
    ) AS details
FROM (
    SELECT 
        EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sync_jobs' AND column_name = 'records_fetched') AS records_fetched_col,
        EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sync_jobs' AND column_name = 'records_saved') AS records_saved_col,
        EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sync_jobs' AND column_name = 'correlation_id') AS correlation_id_col,
        EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sync_jobs' AND column_name = 'items_processed') AS items_preserved,
        NOT EXISTS (
            SELECT 1 FROM sync_jobs 
            WHERE items_processed IS NOT NULL 
              AND items_processed > 0 
              AND (records_fetched = 0 OR records_saved = 0)
        ) AS data_ok,
        EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'sync_jobs' AND indexname = 'idx_sync_jobs_correlation_id') AS idx_exists
) checks;
-- Expected: status = '✅ PASS', all details = true
