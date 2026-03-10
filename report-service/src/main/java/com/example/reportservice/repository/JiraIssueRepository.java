package com.example.reportservice.repository;

import com.example.reportservice.entity.JiraIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JiraIssueRepository
        extends JpaRepository<JiraIssue, Long> {

    List<JiraIssue> findByProjectConfigId(Long projectConfigId);
}
