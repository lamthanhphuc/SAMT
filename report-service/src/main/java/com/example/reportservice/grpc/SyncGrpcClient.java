package com.example.reportservice.grpc;

import com.example.reportservice.grpc.*;
import com.example.reportservice.web.UpstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.UUID;


@Service
public class SyncGrpcClient {

    @GrpcClient("sync-service")
    private SyncServiceGrpc.SyncServiceBlockingStub stub;

    @Retry(name = "syncGrpc")
    @CircuitBreaker(name = "syncGrpc")
    public List<IssueResponse> getIssues(Long projectConfigId) {

        ProjectConfigRequest request =
                ProjectConfigRequest.newBuilder()
                        .setProjectConfigId(projectConfigId.toString())
                        .build();

        IssueListResponse response;
        try {
            response = stub
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getIssuesByProjectConfig(request);
        } catch (StatusRuntimeException ex) {
            throw new UpstreamServiceException("sync-service unavailable", ex);
        }

        return response.getIssuesList();
    }
}
