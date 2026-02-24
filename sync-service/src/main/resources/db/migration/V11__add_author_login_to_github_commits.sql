-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- ==============================================
-- Version: V11
-- Description: Add author_login column to github_commits
-- Purpose: Fix schema validation failure (missing author_login)
-- Author: Backend Engineering Team
-- Date: 2026-02-22
-- ==============================================

-- Add author_login column (nullable, production-safe)
ALTER TABLE github_commits 
    ADD COLUMN IF NOT EXISTS author_login VARCHAR(255);

-- Add partial index for author_login lookups
CREATE INDEX IF NOT EXISTS idx_github_commits_author_login 
    ON github_commits(author_login) 
    WHERE author_login IS NOT NULL;

-- Add column comment
COMMENT ON COLUMN github_commits.author_login IS 'GitHub username of commit author (e.g., octocat)';
