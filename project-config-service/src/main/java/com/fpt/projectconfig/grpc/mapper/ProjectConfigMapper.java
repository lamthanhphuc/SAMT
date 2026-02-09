package com.fpt.projectconfig.grpc.mapper;

import com.fpt.projectconfig.dto.response.ConfigResponse;
import com.fpt.projectconfig.dto.response.VerificationResponse;
import com.fpt.projectconfig.entity.ProjectConfig;
import com.google.protobuf.Timestamp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Mapper chuyển đổi giữa Domain entities/DTOs và Protobuf messages
 * 
 * TODO: Update tên class và method signatures khi có .proto definition
 * Assumption: .proto file sẽ define các message types:
 * - ProjectConfigProto
 * - VerificationResultProto
 * - CreateConfigRequestProto
 * - UpdateConfigRequestProto
 */
public class ProjectConfigMapper {

    /**
     * Convert ConfigResponse (DTO) sang Protobuf message
     * 
     * TODO: Replace return type với generated Protobuf class từ .proto
     * Example: public static ProjectConfigProto toProto(ConfigResponse response)
     */
    public static Object toProto(ConfigResponse response) {
        // TODO: Implement khi có generated Protobuf classes
        // return ProjectConfigProto.newBuilder()
        //     .setId(response.getId().toString())
        //     .setGroupId(response.getGroupId())
        //     .setJiraHostUrl(response.getJiraHostUrl())
        //     .setJiraApiToken(response.getJiraApiToken())  // Masked
        //     .setGithubRepoUrl(response.getGithubRepoUrl())
        //     .setGithubAccessToken(response.getGithubAccessToken())  // Masked
        //     .setState(response.getState().name())
        //     .setInvalidReason(response.getInvalidReason() != null ? response.getInvalidReason() : "")
        //     .setCreatedAt(toProtoTimestamp(response.getCreatedAt()))
        //     .setUpdatedAt(toProtoTimestamp(response.getUpdatedAt()))
        //     .setCreatedBy(response.getCreatedBy())
        //     .setUpdatedBy(response.getUpdatedBy())
        //     .build();
        throw new UnsupportedOperationException("Implement after .proto generation");
    }

    /**
     * Convert VerificationResponse sang Protobuf
     */
    public static Object toVerificationProto(VerificationResponse response) {
        // TODO: Implement khi có generated Protobuf classes
        // return VerificationResultProto.newBuilder()
        //     .setConfigId(response.getConfigId().toString())
        //     .setGroupId(response.getGroupId())
        //     .setOverallSuccess(response.isOverallSuccess())
        //     .setState(response.getState().name())
        //     .setJiraVerified(response.getJiraResult().isSuccess())
        //     .setJiraMessage(response.getJiraResult().getMessage())
        //     .setGithubVerified(response.getGithubResult().isSuccess())
        //     .setGithubMessage(response.getGithubResult().getMessage())
        //     .build();
        throw new UnsupportedOperationException("Implement after .proto generation");
    }

    /**
     * Convert Protobuf Timestamp sang LocalDateTime
     */
    public static LocalDateTime fromProtoTimestamp(Timestamp timestamp) {
        return LocalDateTime.ofEpochSecond(
                timestamp.getSeconds(),
                timestamp.getNanos(),
                ZoneOffset.UTC
        );
    }

    /**
     * Convert LocalDateTime sang Protobuf Timestamp
     */
    public static Timestamp toProtoTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return Timestamp.getDefaultInstance();
        }
        long seconds = dateTime.toEpochSecond(ZoneOffset.UTC);
        int nanos = dateTime.getNano();
        return Timestamp.newBuilder()
                .setSeconds(seconds)
                .setNanos(nanos)
                .build();
    }
}
