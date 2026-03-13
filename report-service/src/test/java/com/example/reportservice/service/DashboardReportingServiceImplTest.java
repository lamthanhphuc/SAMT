package com.example.reportservice.service;

import com.example.reportservice.client.ProjectConfigClient;
import com.example.reportservice.client.UserGroupClient;
import com.example.reportservice.repository.GithubCommitRepository;
import com.example.reportservice.repository.JiraIssueRepository;
import com.example.reportservice.repository.SyncJobRepository;
import com.example.reportservice.repository.UnifiedActivityRepository;
import com.example.reportservice.service.impl.DashboardReportingServiceImpl;
import com.example.reportservice.web.UpstreamServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardReportingServiceImplTest {

    @Mock
    private UserGroupClient userGroupClient;

    @Mock
    private ProjectConfigClient projectConfigClient;

    @Mock
    private JiraIssueRepository jiraIssueRepository;

    @Mock
    private UnifiedActivityRepository unifiedActivityRepository;

    @Mock
    private GithubCommitRepository githubCommitRepository;

    @Mock
    private SyncJobRepository syncJobRepository;

    @InjectMocks
    private DashboardReportingServiceImpl service;

    @Test
    void getStudentTasksShouldReturnEmptyPageWhenUpstreamUnavailable() {
        when(userGroupClient.getUserProfile(101L)).thenThrow(new UpstreamServiceException("user-group-service unavailable"));

        var response = service.getStudentTasks(101L, null, null, 0, 20);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
        assertThat(response.getTotalPages()).isEqualTo(0);
    }

    @Test
    void getStudentTasksShouldReturnEmptyPageWhenMembershipNotFound() {
        when(userGroupClient.getUserProfile(202L)).thenReturn(
            new UserGroupClient.UserProfile(202L, "student@example.com", "Student Name")
        );
        when(userGroupClient.getUserGroups(202L)).thenReturn(List.of());

        var response = service.getStudentTasks(202L, null, null, 0, 20);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
    }
}
