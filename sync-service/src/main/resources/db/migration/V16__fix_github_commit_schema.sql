-- Fix schema mismatch between GithubCommit entity and github_commits table
-- Rename commit_message to message (idempotent)
-- Add missing columns: author_login, total_changes

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'github_commits' AND column_name = 'commit_message'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'github_commits' AND column_name = 'message'
    ) THEN
        ALTER TABLE github_commits RENAME COLUMN commit_message TO message;
    END IF;
END $$;

ALTER TABLE github_commits
    ADD COLUMN IF NOT EXISTS author_login VARCHAR(255);

ALTER TABLE github_commits
    ADD COLUMN IF NOT EXISTS total_changes INTEGER DEFAULT 0;

COMMENT ON COLUMN github_commits.message IS 'Git commit message';
COMMENT ON COLUMN github_commits.author_login IS 'GitHub username of commit author';
COMMENT ON COLUMN github_commits.total_changes IS 'Total lines changed (additions + deletions)';
