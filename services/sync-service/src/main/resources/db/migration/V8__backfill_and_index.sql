-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- Version: V8 (Transactional Part)
-- Data backfill and index preparation
-- ==============================================

-- ==============================================
-- STEP 1: Acquire advisory lock
-- ==============================================

DO $$
BEGIN
    IF NOT pg_try_advisory_lock(hashtext('V8_backfill_sync_jobs')::bigint) THEN
        RAISE NOTICE 'V8: Another instance is running migration. Exiting gracefully.';
        RETURN;
    END IF;

    RAISE NOTICE 'V8: Advisory lock acquired.';
END $$;

-- ==============================================
-- STEP 2: Backfill (PK-optimized batching)
-- ==============================================

DO $$
DECLARE
    rows_updated INT;
BEGIN
    RAISE NOTICE 'V8: Starting backfill';

    LOOP
        WITH batch AS (
            SELECT id
            FROM sync_jobs
            WHERE records_fetched = 0
              AND records_saved = 0
              AND items_processed IS NOT NULL
              AND items_processed > 0
            ORDER BY id
            LIMIT 5000
            FOR UPDATE SKIP LOCKED
        )
        UPDATE sync_jobs s
        SET records_fetched = COALESCE(s.items_processed,0),
            records_saved   = COALESCE(s.items_processed,0)
        FROM batch
        WHERE s.id = batch.id;

        GET DIAGNOSTICS rows_updated = ROW_COUNT;

        EXIT WHEN rows_updated = 0;

        PERFORM pg_sleep(0.1);
    END LOOP;

    RAISE NOTICE 'V8: Backfill complete';
END $$;

ANALYZE sync_jobs;

-- ==============================================
-- STEP 3: Rename INVALID indexes (preparation)
-- ==============================================

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_index i ON c.oid = i.indexrelid
        WHERE c.relname IN ('idx_sync_jobs_correlation_id',
                            'idx_sync_jobs_status_records')
          AND NOT i.indisvalid
    LOOP
        EXECUTE format(
            'ALTER INDEX %I RENAME TO %I',
            r.relname,
            r.relname || '_invalid_drop'
        );
        RAISE NOTICE 'V8: Prepared INVALID index % for drop', r.relname;
    END LOOP;
END $$;

-- ==============================================
-- STEP 4: Verification
-- ==============================================

DO $$
DECLARE
    pending INT;
BEGIN
    SELECT COUNT(*) INTO pending
    FROM sync_jobs
    WHERE records_fetched = 0
      AND records_saved = 0
      AND items_processed IS NOT NULL
      AND items_processed > 0;

    IF pending > 0 THEN
        RAISE WARNING 'V8: % rows still pending backfill (safe to rerun)', pending;
    ELSE
        RAISE NOTICE 'V8: Backfill verified - ready for V9 index creation';
    END IF;
END $$;

-- ==============================================
-- STEP 5: Release advisory lock
-- ==============================================

SELECT pg_advisory_unlock(hashtext('V8_backfill_sync_jobs')::bigint);

-- ==============================================
-- END OF V8 - Proceed to V9 for index creation
-- ==============================================