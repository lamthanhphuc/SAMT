-- Add optimistic locking version columns to support concurrent update detection
-- This migration adds version columns with DEFAULT 0 to work with existing production data

ALTER TABLE groups
ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE user_semester_membership
ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
