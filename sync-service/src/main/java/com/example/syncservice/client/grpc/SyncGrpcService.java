package com.example.syncservice.client.grpc;

import com.example.syncservice.client.grpc.*;
import com.example.syncservice.entity.JiraIssue;
import com.example.syncservice.repository.JiraIssueRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class SyncGrpcService extends SyncServiceGrpc.SyncServiceImplBase {

    private final JiraIssueRepository jiraIssueRepository;

    @Override
    public void getIssuesByProjectConfig(
            ProjectConfigRequest request,
            StreamObserver<IssueListResponse> responseObserver) {

        try {
            UUID projectConfigId;
            try {
                projectConfigId = UUID.fromString(request.getProjectConfigId());
            } catch (IllegalArgumentException ex) {
                responseObserver.onError(
                    Status.INVALID_ARGUMENT
                        .withDescription("projectConfigId must be a valid UUID")
                        .asRuntimeException()
                );
                return;
            }

            List<JiraIssue> issues =
                    jiraIssueRepository.findByProjectConfigIdAndDeletedAtIsNull(projectConfigId);

            IssueListResponse.Builder response =
                    IssueListResponse.newBuilder();

            for (JiraIssue issue : issues) {

                response.addIssues(
                        IssueResponse.newBuilder()
                                .setIssueId(issue.getIssueId())
                                .setIssueKey(issue.getIssueKey())
                                .setSummary(issue.getSummary())
                                .setDescription(nullSafe(issue.getDescription()))
                                .setIssueType(nullSafe(issue.getIssueType()))
                                .setStatus(nullSafe(issue.getStatus()))
                                .setPriority(nullSafe(issue.getPriority()))
                                .setAssigneeEmail(nullSafe(issue.getAssigneeEmail()))
                                .setAssigneeName(nullSafe(issue.getAssigneeName()))
                                .setReporterEmail(nullSafe(issue.getReporterEmail()))
                                .setReporterName(nullSafe(issue.getReporterName()))
                                .build()
                );
            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();

        } catch (Exception ex) {

            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Failed to fetch issues")
                            .withCause(ex)
                            .asRuntimeException()
            );
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
