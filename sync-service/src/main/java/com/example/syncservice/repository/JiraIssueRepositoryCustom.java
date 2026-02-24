package com.example.syncservice.repository;

import com.example.syncservice.entity.JiraIssue;

import java.util.List;

/**
 * Custom repository interface for JiraIssue UPSERT operations.
 */
public interface JiraIssueRepositoryCustom {
    
    /**
     * Batch UPSERT for Jira issues using PostgreSQL ON CONFLICT.
     * Idempotent: safe to call multiple times with same data.
     * 
     * @param issues List of JiraIssue entities to upsert
     * @return Number of rows affected (inserted + updated)
     */
    int upsertBatch(List<JiraIssue> issues);
    
    /**
     * Single UPSERT for one Jira issue.
     * 
     * @param issue JiraIssue entity to upsert
     * @return 1 if inserted or updated, 0 otherwise
     */
    int upsert(JiraIssue issue);
}
