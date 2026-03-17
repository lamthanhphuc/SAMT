package com.example.reportservice.repository;

import com.example.reportservice.entity.SyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

  long countByProjectConfigIdInAndStatus(List<UUID> projectConfigIds, String status);

  Optional<SyncJob> findFirstByProjectConfigIdInAndJobTypeOrderByCreatedAtDesc(List<UUID> projectConfigIds, String jobType);

    @Query("""
        select max(s.completedAt) from SyncJob s
        where s.projectConfigId in :projectConfigIds
          and s.completedAt is not null
        """)
    LocalDateTime findLastCompletedAt(@Param("projectConfigIds") List<UUID> projectConfigIds);
}