package com.example.user_groupservice.grpc;

import com.example.user_groupservice.grpc.VerifyGroupRequest;
import com.example.user_groupservice.grpc.VerifyGroupResponse;
import com.example.user_groupservice.grpc.CheckGroupLeaderRequest;
import com.example.user_groupservice.grpc.CheckGroupLeaderResponse;
import com.example.user_groupservice.grpc.CheckGroupMemberRequest;
import com.example.user_groupservice.grpc.CheckGroupMemberResponse;
import com.example.user_groupservice.grpc.GetGroupRequest;
import com.example.user_groupservice.grpc.GetGroupResponse;
import com.example.user_groupservice.entity.Group;
import com.example.user_groupservice.entity.GroupRole;
import com.example.user_groupservice.entity.UserGroup;
import com.example.user_groupservice.repository.GroupRepository;
import com.example.user_groupservice.repository.UserGroupRepository;
import com.example.user_groupservice.grpc.UserGroupGrpcServiceGrpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;



import java.util.Optional;
import java.util.UUID;

/**
 * gRPC Service Implementation for User-Group Service.
 * Provides group validation and membership checking to other microservices.
 * 
 * Used by Project Config Service to:
 * - Verify group exists before creating configs
 * - Check if user is group leader (authorization)
 * - Check if user is group member (authorization)
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class UserGroupGrpcServiceImpl extends UserGroupGrpcServiceGrpc.UserGroupGrpcServiceImplBase {

    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;

    /**
     * Verify if a group exists and is not soft-deleted.
     * 
     * @param request Contains group_id (UUID as string)
     * @param responseObserver Returns exists, deleted flags and message
     */
    @Override
    @Transactional(readOnly = true)
    public void verifyGroupExists(VerifyGroupRequest request, StreamObserver<VerifyGroupResponse> responseObserver) {
        try {
            UUID groupId = UUID.fromString(request.getGroupId());
            log.info("gRPC VerifyGroupExists called: groupId={}", groupId);

            Optional<Group> groupOpt = groupRepository.findById(groupId);
            
            boolean exists = groupOpt.isPresent();
            boolean deleted = exists && groupOpt.get().getDeletedAt() != null;
            String message = exists ? 
                    (deleted ? "Group exists but is deleted" : "Group exists and is active") :
                    "Group not found";
            
            VerifyGroupResponse response = VerifyGroupResponse.newBuilder()
                    .setExists(exists)
                    .setDeleted(deleted)
                    .setMessage(message)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("gRPC VerifyGroupExists completed: groupId={}, exists={}, deleted={}", 
                    groupId, exists, deleted);

        } catch (IllegalArgumentException e) {
            log.error("Invalid group ID format: {}", request.getGroupId());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid group ID format - must be valid UUID")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in gRPC VerifyGroupExists: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }

    /**
     * Check if a user is the LEADER of a group.
     * 
     * @param request Contains group_id (UUID) and user_id (Long)
     * @param responseObserver Returns is_leader boolean and message
     */
    @Override
    @Transactional(readOnly = true)
    public void checkGroupLeader(CheckGroupLeaderRequest request, StreamObserver<CheckGroupLeaderResponse> responseObserver) {
        try {
            UUID groupId = UUID.fromString(request.getGroupId());
            Long userId = Long.parseLong(request.getUserId());
            log.info("gRPC CheckGroupLeader called: groupId={}, userId={}", groupId, userId);

            // Check if membership exists first
            Optional<UserGroup> membershipOpt = userGroupRepository.findByUserIdAndGroupId(userId, groupId);
            
            boolean isLeader = membershipOpt.isPresent() && 
                              membershipOpt.get().getRole() == GroupRole.LEADER;
            
            String message = membershipOpt.isEmpty() ? 
                    "User is not a member of this group" :
                    (isLeader ? "User is the leader" : "User is a member but not the leader");
            
            CheckGroupLeaderResponse response = CheckGroupLeaderResponse.newBuilder()
                    .setIsLeader(isLeader)
                    .setMessage(message)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("gRPC CheckGroupLeader completed: groupId={}, userId={}, isLeader={}", 
                    groupId, userId, isLeader);

        } catch (IllegalArgumentException e) {
            log.error("Invalid ID format: groupId={}, userId={}", request.getGroupId(), request.getUserId());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid ID format - groupId must be UUID, userId must be Long")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in gRPC CheckGroupLeader: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }

    /**
     * Check if a user is a MEMBER of a group (any role: LEADER or MEMBER).
     * 
     * @param request Contains group_id (UUID) and user_id (Long)
     * @param responseObserver Returns is_member boolean, role string, and message
     */
    @Override
    @Transactional(readOnly = true)
    public void checkGroupMember(CheckGroupMemberRequest request, StreamObserver<CheckGroupMemberResponse> responseObserver) {
        try {
            UUID groupId = UUID.fromString(request.getGroupId());
            Long userId = Long.parseLong(request.getUserId());
            log.info("gRPC CheckGroupMember called: groupId={}, userId={}", groupId, userId);

            Optional<UserGroup> membershipOpt = userGroupRepository.findByUserIdAndGroupId(userId, groupId);
            
            boolean isMember = membershipOpt.isPresent();
            String role = isMember ? membershipOpt.get().getRole().name() : "";
            String message = isMember ? 
                    "User is a member with role: " + role :
                    "User is not a member of this group";
            
            CheckGroupMemberResponse response = CheckGroupMemberResponse.newBuilder()
                    .setIsMember(isMember)
                    .setRole(role)
                    .setMessage(message)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("gRPC CheckGroupMember completed: groupId={}, userId={}, isMember={}, role={}", 
                    groupId, userId, isMember, role);

        } catch (IllegalArgumentException e) {
            log.error("Invalid ID format: groupId={}, userId={}", request.getGroupId(), request.getUserId());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid ID format - groupId must be UUID, userId must be Long")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in gRPC CheckGroupMember: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }

    /**
     * Get group details (optional - for future use).
     * 
     * @param request Contains group_id (UUID)
     * @param responseObserver Returns group details
     */
    @Override
    @Transactional(readOnly = true)
    public void getGroup(GetGroupRequest request, StreamObserver<GetGroupResponse> responseObserver) {
        try {
            UUID groupId = UUID.fromString(request.getGroupId());
            log.info("gRPC GetGroup called: groupId={}", groupId);

            Optional<Group> groupOpt = groupRepository.findById(groupId);
            
            if (groupOpt.isEmpty()) {
                log.warn("Group not found: groupId={}", groupId);
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Group not found or deleted")
                        .asRuntimeException());
                return;
            }

            Group group = groupOpt.get();
            GetGroupResponse response = GetGroupResponse.newBuilder()
                    .setGroupId(group.getId().toString())
                    .setGroupName(group.getGroupName())
                    .setSemester(group.getSemester())
                    .setLecturerId(group.getLecturerId().toString())
                    .setCreatedAt(group.getCreatedAt().toString())
                    .setUpdatedAt(group.getUpdatedAt().toString())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("gRPC GetGroup completed: groupId={}", groupId);

        } catch (IllegalArgumentException e) {
            log.error("Invalid group ID format: {}", request.getGroupId());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid group ID format - must be valid UUID")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in gRPC GetGroup: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }
}
