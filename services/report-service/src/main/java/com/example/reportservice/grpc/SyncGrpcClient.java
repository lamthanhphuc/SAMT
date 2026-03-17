package com.example.reportservice.grpc;

import com.example.reportservice.grpc.*;
import com.example.reportservice.web.UpstreamServiceException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.persistence.EntityNotFoundException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.UUID;


@Service
public class SyncGrpcClient {

    @GrpcClient("sync-service")
    private SyncServiceGrpc.SyncServiceBlockingStub stub;

    public List<IssueResponse> getIssues(UUID projectConfigId) {

        return executeWithStandardErrorHandling(() -> {
            ProjectConfigRequest request =
                ProjectConfigRequest.newBuilder()
                    .setProjectConfigId(projectConfigId.toString())
                    .build();

            IssueListResponse response = stub
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .getIssuesByProjectConfig(request);
            return response.getIssuesList();
        });
    }

    public List<IssueResponse> getIssues(Long projectConfigId) {
        return executeWithStandardErrorHandling(() -> {
            ProjectConfigRequest request =
                ProjectConfigRequest.newBuilder()
                    .setProjectConfigId(projectConfigId.toString())
                    .build();

            IssueListResponse response = stub
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .getIssuesByProjectConfig(request);
            return response.getIssuesList();
        });
    }

    public List<GithubCommitResponse> getGithubCommits(UUID projectConfigId) {

        return executeWithStandardErrorHandling(() -> {
            ProjectConfigRequest request =
                ProjectConfigRequest.newBuilder()
                    .setProjectConfigId(projectConfigId.toString())
                    .build();

            GithubCommitListResponse response = stub
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .getGithubCommitsByProjectConfig(request);
            return response.getCommitsList();
        });
    }

    public List<UnifiedActivityResponse> getUnifiedActivities(UUID projectConfigId) {

        return executeWithStandardErrorHandling(() -> {
            ProjectConfigRequest request =
                ProjectConfigRequest.newBuilder()
                    .setProjectConfigId(projectConfigId.toString())
                    .build();

            UnifiedActivityListResponse response = stub
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .getUnifiedActivitiesByProjectConfig(request);
            return response.getActivitiesList();
        });
    }

    private <T> T executeWithStandardErrorHandling(GrpcCall<T> call) {
        try {
            return call.execute();
        } catch (StatusRuntimeException ex) {
            Status.Code statusCode = ex.getStatus().getCode();
            if (statusCode == Status.Code.INVALID_ARGUMENT) {
                throw new EntityNotFoundException("Project configuration not found");
            }
            if (statusCode == Status.Code.NOT_FOUND) {
                throw new EntityNotFoundException("Project configuration not found");
            }
            throw new UpstreamServiceException("sync-service unavailable", ex);
        }
    }

    @FunctionalInterface
    private interface GrpcCall<T> {
        T execute();
    }
}
