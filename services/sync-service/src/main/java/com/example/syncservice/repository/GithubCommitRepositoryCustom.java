package com.example.syncservice.repository;

import com.example.syncservice.entity.GithubCommit;

import java.util.List;

/**
 * Custom repository interface for GithubCommit UPSERT operations.
 */
public interface GithubCommitRepositoryCustom {
    
    /**
     * Batch UPSERT for GitHub commits using PostgreSQL ON CONFLICT.
     * Idempotent: safe to call multiple times with same data.
     * 
     * @param commits List of GithubCommit entities to upsert
     * @return Number of rows affected (inserted + updated)
     */
    int upsertBatch(List<GithubCommit> commits);
    
    /**
     * Single UPSERT for one GitHub commit.
     * 
     * @param commit GithubCommit entity to upsert
     * @return 1 if inserted or updated, 0 otherwise
     */
    int upsert(GithubCommit commit);
}
