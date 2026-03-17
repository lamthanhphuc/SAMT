package com.example.reportservice.service;

public interface JiraService {

    void assignIssue(String issueKey, String accountId);

    String transitionIssueToStatus(String issueKey, String status);
}
