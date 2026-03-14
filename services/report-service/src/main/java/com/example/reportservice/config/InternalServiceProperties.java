package com.example.reportservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report.integrations")
public class InternalServiceProperties {

    private String userGroupBaseUrl = "http://user-group-service:8082";
    private String projectConfigBaseUrl = "http://project-config-service:8084";

    public String getUserGroupBaseUrl() {
        return userGroupBaseUrl;
    }

    public void setUserGroupBaseUrl(String userGroupBaseUrl) {
        this.userGroupBaseUrl = userGroupBaseUrl;
    }

    public String getProjectConfigBaseUrl() {
        return projectConfigBaseUrl;
    }

    public void setProjectConfigBaseUrl(String projectConfigBaseUrl) {
        this.projectConfigBaseUrl = projectConfigBaseUrl;
    }
}