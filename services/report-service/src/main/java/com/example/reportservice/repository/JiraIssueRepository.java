package com.example.reportservice.repository;

import com.example.reportservice.entity.JiraIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JiraIssueRepository
        extends JpaRepository<JiraIssue, Long> {

        List<JiraIssue> findByProjectConfigId(UUID projectConfigId);

        List<JiraIssue> findByProjectConfigIdIn(List<UUID> projectConfigIds);

        List<JiraIssue> findByProjectConfigIdInAndAssigneeEmailIgnoreCase(List<UUID> projectConfigIds, String assigneeEmail);

        Page<JiraIssue> findByProjectConfigIdInAndAssigneeEmailIgnoreCase(List<UUID> projectConfigIds, String assigneeEmail, Pageable pageable);

        long countByProjectConfigIdInAndAssigneeEmailIgnoreCase(List<UUID> projectConfigIds, String assigneeEmail);

        @Query("""
                select count(j) from JiraIssue j
                where j.projectConfigId in :projectConfigIds
                    and lower(coalesce(j.status, '')) in :completedStatuses
                """)
        long countCompletedIssues(@Param("projectConfigIds") List<UUID> projectConfigIds,
                                                            @Param("completedStatuses") List<String> completedStatuses);

        @Query("""
                select count(j) from JiraIssue j
                where j.projectConfigId in :projectConfigIds
                    and lower(coalesce(j.status, '')) in :completedStatuses
                    and lower(coalesce(j.assigneeEmail, '')) = lower(:assigneeEmail)
                """)
        long countCompletedIssuesForAssignee(@Param("projectConfigIds") List<UUID> projectConfigIds,
                                                                                 @Param("assigneeEmail") String assigneeEmail,
                                                                                 @Param("completedStatuses") List<String> completedStatuses);

        @Query("""
                select j from JiraIssue j
                where j.projectConfigId = :projectConfigId
                  and (j.issueId = :taskId or lower(coalesce(j.issueKey, '')) = lower(:taskId))
                """)
        Optional<JiraIssue> findTaskByProjectConfigAndTaskId(@Param("projectConfigId") UUID projectConfigId,
                                                              @Param("taskId") String taskId);
}
