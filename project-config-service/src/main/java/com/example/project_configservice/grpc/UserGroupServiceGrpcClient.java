package com.example.project_configservice.grpc;

import com.example.project_configservice.grpc.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client for User-Group Service integration.
 * Provides group validation and membership checking methods.
 * 
 * Used by Project Config Service to:
 * - Verify group exists before creating configs
 * - Check if user is group leader (for authorization)
 * - Check if user is group member (for authorization)
 */
@Service
@Slf4j
public class UserGroupServiceGrpcClient {
    
    @GrpcClient("user-group-service")
    private UserGroupGrpcServiceGrpc.UserGroupGrpcServiceBlockingStub groupStub;
    
    @Value("${grpc.client.user-group-service.deadline-seconds:3}")
    private long deadlineSeconds;
    
    /**
     * Verify group exists and is not deleted.
     * 
     * @param groupId UUID of the group
     * @return VerifyGroupResponse with exists and deleted flags
     * @throws StatusRuntimeException if gRPC call fails
     */
    public VerifyGroupResponse verifyGroupExists(UUID groupId) {
        log.debug("Verifying group exists: {}", groupId);
        
        try {
            VerifyGroupRequest request = VerifyGroupRequest.newBuilder()
                .setGroupId(groupId.toString())
                .build();
            
            return groupStub
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                .verifyGroupExists(request);
                
        } catch (StatusRuntimeException e) {
            log.error("Failed to verify group {}: {}", groupId, e.getStatus());
            throw e;
        }
    }
    
    /**
     * Check if user is LEADER of a group.
     * 
     * @param groupId UUID of the group
     * @param userId User ID to check
     * @return CheckGroupLeaderResponse with is_leader boolean
     * @throws StatusRuntimeException if gRPC call fails
     */
    public CheckGroupLeaderResponse checkGroupLeader(UUID groupId, Long userId) {
        log.debug("Checking if user {} is leader of group {}", userId, groupId);
        
        try {
            CheckGroupLeaderRequest request = CheckGroupLeaderRequest.newBuilder()
                .setGroupId(groupId.toString())
                .setUserId(userId.toString())
                .build();
            
            return groupStub
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                .checkGroupLeader(request);
                
        } catch (StatusRuntimeException e) {
            log.error("Failed to check group leader: group={}, user={}, error={}", 
                groupId, userId, e.getStatus());
            throw e;
        }
    }
    
    /**
     * Check if user is a MEMBER of a group (any role: LEADER or MEMBER).
     * 
     * @param groupId UUID of the group
     * @param userId User ID to check
     * @return CheckGroupMemberResponse with is_member boolean and role
     * @throws StatusRuntimeException if gRPC call fails
     */
    public CheckGroupMemberResponse checkGroupMember(UUID groupId, Long userId) {
        log.debug("Checking if user {} is member of group {}", userId, groupId);
        
        try {
            CheckGroupMemberRequest request = CheckGroupMemberRequest.newBuilder()
                .setGroupId(groupId.toString())
                .setUserId(userId.toString())
                .build();
            
            return groupStub
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                .checkGroupMember(request);
                
        } catch (StatusRuntimeException e) {
            log.error("Failed to check group member: group={}, user={}, error={}", 
                groupId, userId, e.getStatus());
            throw e;
        }
    }
    
    /**
     * Get group details with basic info (optional - for future use).
     * 
     * @param groupId UUID of the group
     * @return GetGroupResponse with group details
     * @throws StatusRuntimeException if gRPC call fails (NOT_FOUND if group doesn't exist)
     */
    public GetGroupResponse getGroup(UUID groupId) {
        log.debug("Fetching group details: {}", groupId);
        
        try {
            GetGroupRequest request = GetGroupRequest.newBuilder()
                .setGroupId(groupId.toString())
                .build();
            
            return groupStub
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                .getGroup(request);
                
        } catch (StatusRuntimeException e) {
            log.error("Failed to fetch group {}: {}", groupId, e.getStatus());
            throw e;
        }
    }
}
