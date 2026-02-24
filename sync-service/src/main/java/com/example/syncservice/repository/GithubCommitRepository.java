package com.example.syncservice.repository;

import com.example.syncservice.entity.GithubCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for GithubCommit entity.
 * Extends custom interface for UPSERT operations.
 */
@Repository
public interface GithubCommitRepository extends JpaRepository<GithubCommit, Long>, GithubCommitRepositoryCustom {

    List<GithubCommit> findByProjectConfigIdAndDeletedAtIsNull(Long projectConfigId);

    Optional<GithubCommit> findByProjectConfigIdAndCommitSha(Long projectConfigId, String commitSha);
}
