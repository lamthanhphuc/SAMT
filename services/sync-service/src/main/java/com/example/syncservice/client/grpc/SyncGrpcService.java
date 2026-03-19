package com.example.syncservice.client.grpc;

import com.example.syncservice.entity.GithubCommit;
import com.example.syncservice.entity.JiraIssue;
import com.example.syncservice.entity.UnifiedActivity;
import com.example.syncservice.repository.GithubCommitRepository;
import com.example.syncservice.repository.JiraIssueRepository;
import com.example.syncservice.repository.UnifiedActivityRepository;
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
    private final GithubCommitRepository githubCommitRepository;
    private final UnifiedActivityRepository unifiedActivityRepository;

    @Override
    public void getIssuesByProjectConfig(
            ProjectConfigRequest request,
            StreamObserver<IssueListResponse> responseObserver) {

        try {
            UUID projectConfigId = parseProjectConfigId(request, responseObserver);
            if (projectConfigId == null) {
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
                        .setCreatedAt(issue.getCreatedAt() == null ? "" : issue.getCreatedAt().toString())
                        .setUpdatedAt(issue.getUpdatedAt() == null ? "" : issue.getUpdatedAt().toString())
                        .setDueDate(issue.getDueDate() == null ? "" : issue.getDueDate().toString())
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

    @Override
    public void getGithubCommitsByProjectConfig(
            ProjectConfigRequest request,
            StreamObserver<GithubCommitListResponse> responseObserver) {

        try {
            UUID projectConfigId = parseProjectConfigId(request, responseObserver);
            if (projectConfigId == null) {
                return;
            }

            List<GithubCommit> commits = githubCommitRepository.findByProjectConfigIdAndDeletedAtIsNull(projectConfigId);
            GithubCommitListResponse.Builder response = GithubCommitListResponse.newBuilder();

            for (GithubCommit commit : commits) {
                response.addCommits(
                    GithubCommitResponse.newBuilder()
                        .setCommitSha(nullSafe(commit.getCommitSha()))
                        .setMessage(nullSafe(commit.getMessage()))
                        .setCommittedDate(commit.getCommittedDate() == null ? "" : commit.getCommittedDate().toString())
                        .setAuthorEmail(nullSafe(commit.getAuthorEmail()))
                        .setAuthorName(nullSafe(commit.getAuthorName()))
                        .setAdditions(commit.getAdditions() == null ? 0 : Math.max(0, commit.getAdditions()))
                        .setDeletions(commit.getDeletions() == null ? 0 : Math.max(0, commit.getDeletions()))
                        .setTotalChanges(commit.getTotalChanges() == null ? 0 : Math.max(0, commit.getTotalChanges()))
                        .build()
                );
            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Failed to fetch github commits")
                    .withCause(ex)
                    .asRuntimeException()
            );
        }
    }

    @Override
    public void getUnifiedActivitiesByProjectConfig(
            ProjectConfigRequest request,
            StreamObserver<UnifiedActivityListResponse> responseObserver) {

        try {
            UUID projectConfigId = parseProjectConfigId(request, responseObserver);
            if (projectConfigId == null) {
                return;
            }

            List<UnifiedActivity> activities = unifiedActivityRepository.findByProjectConfigIdAndDeletedAtIsNull(projectConfigId);
            UnifiedActivityListResponse.Builder response = UnifiedActivityListResponse.newBuilder();

            for (UnifiedActivity activity : activities) {
                response.addActivities(
                    UnifiedActivityResponse.newBuilder()
                        .setSource(activity.getSource() == null ? "" : activity.getSource().name())
                        .setActivityType(activity.getActivityType() == null ? "" : activity.getActivityType().name())
                        .setExternalId(nullSafe(activity.getExternalId()))
                        .setTitle(nullSafe(activity.getTitle()))
                        .setDescription(nullSafe(activity.getDescription()))
                        .setAuthorEmail(nullSafe(activity.getAuthorEmail()))
                        .setAuthorName(nullSafe(activity.getAuthorName()))
                        .setStatus(nullSafe(activity.getStatus()))
                        .setCreatedAt(activity.getCreatedAt() == null ? "" : activity.getCreatedAt().toString())
                        .setUpdatedAt(activity.getUpdatedAt() == null ? "" : activity.getUpdatedAt().toString())
                        .build()
                );
            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Failed to fetch unified activities")
                    .withCause(ex)
                    .asRuntimeException()
            );
        }
    }

    private UUID parseProjectConfigId(ProjectConfigRequest request, StreamObserver<?> responseObserver) {
        try {
            return UUID.fromString(request.getProjectConfigId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("projectConfigId must be a valid UUID")
                    .asRuntimeException()
            );
            return null;
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
