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
            Status.Code statusCode = ex.getStatus().getCode();
            if (statusCode == Status.Code.INVALID_ARGUMENT) {
                throw new EntityNotFoundException("Project configuration not found");
            }
            if (statusCode == Status.Code.NOT_FOUND) {
                throw new EntityNotFoundException("Project configuration not found");
            }
            throw new UpstreamServiceException("sync-service unavailable", ex);
        }

        return response.getIssuesList();
    }
}
