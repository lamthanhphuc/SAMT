package com.example.user_groupservice.service.impl;

import com.example.user_groupservice.dto.request.CreateGroupRequest;
import com.example.user_groupservice.dto.request.UpdateGroupRequest;
import com.example.user_groupservice.dto.request.UpdateLecturerRequest;
import com.example.user_groupservice.dto.response.GroupDetailResponse;
import com.example.user_groupservice.dto.response.GroupListResponse;
import com.example.user_groupservice.dto.response.GroupResponse;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.entity.Group;
import com.example.user_groupservice.entity.UserGroup;
import com.example.user_groupservice.exception.*;
import com.example.user_groupservice.grpc.IdentityServiceClient;
import com.example.user_groupservice.repository.GroupRepository;
import com.example.user_groupservice.repository.UserGroupRepository;
import com.example.user_groupservice.service.GroupService;
import com.samt.identity.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of GroupService.
 * Handles group CRUD operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GroupServiceImpl implements GroupService {
    
    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final IdentityServiceClient identityServiceClient;
    
    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        log.info("Creating group: groupName={}, semester={}", 
                request.getGroupName(), request.getSemester());
        
        // Check group name uniqueness in semester
        if (groupRepository.existsByGroupNameAndSemester(
                request.getGroupName(), request.getSemester())) {
            throw ConflictException.groupNameDuplicate(
                    request.getGroupName(), request.getSemester());
        }
        
        // Validate lecturer exists and has LECTURER role via gRPC
        try {
            VerifyUserResponse verification = identityServiceClient.verifyUserExists(
                    request.getLecturerId());
            
            if (!verification.getExists()) {
                throw ResourceNotFoundException.lecturerNotFound(request.getLecturerId());
            }
            
            if (!verification.getActive()) {
                throw new ConflictException("LECTURER_INACTIVE", 
                        "Lecturer account is not active");
            }
            
            // Verify LECTURER role
            GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(
                    request.getLecturerId());
            
            if (roleResponse.getRole() != UserRole.LECTURER) {
                throw new ConflictException("INVALID_ROLE", 
                        "User is not a lecturer: " + roleResponse.getRole());
            }
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when validating lecturer: {}", e.getStatus());
            throw new RuntimeException("Failed to validate lecturer: " + e.getStatus().getCode());
        }
        
        Group group = Group.builder()
                .groupName(request.getGroupName())
                .semester(request.getSemester())
                .lecturerId(request.getLecturerId())
                .build();
        
        Group savedGroup = groupRepository.save(group);
        log.info("Group created successfully: groupId={}", savedGroup.getId());
        
        // Fetch lecturer info for response
        GetUserResponse lecturerInfo = identityServiceClient.getUser(request.getLecturerId());
        
        return GroupResponse.builder()
                .id(savedGroup.getId())
                .groupName(savedGroup.getGroupName())
                .semester(savedGroup.getSemester())
                .lecturerId(savedGroup.getLecturerId())
                .lecturerName(lecturerInfo.getFullName())
                .build();
    }
    
    @Override
    public GroupDetailResponse getGroupById(UUID groupId) {
        log.info("Getting group details: groupId={}", groupId);
        
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Fetch members
        List<UserGroup> memberships = userGroupRepository.findAllByGroupId(groupId);
        
        // Batch fetch user info from Identity Service (avoid N+1 calls)
        List<UUID> userIds = memberships.stream()
                .map(UserGroup::getUserId)
                .toList();
        
        GetUsersResponse usersResponse = identityServiceClient.getUsers(userIds);
        
        // Map user info to member list
        List<GroupDetailResponse.MemberInfo> members = memberships.stream()
                .map(ug -> {
                    GetUserResponse userInfo = usersResponse.getUsersList().stream()
                            .filter(u -> u.getUserId().equals(ug.getUserId().toString()))
                            .findFirst()
                            .orElse(null);
                    
                    return GroupDetailResponse.MemberInfo.builder()
                            .userId(ug.getUserId())
                            .fullName(userInfo != null ? userInfo.getFullName() : "<Deleted User>")
                            .email(userInfo != null && !userInfo.getDeleted() ? userInfo.getEmail() : null)
                            .role(ug.getRole().name())
                            .build();
                })
                .collect(Collectors.toList());
        
        // Fetch lecturer info
        GetUserResponse lecturerInfo = identityServiceClient.getUser(group.getLecturerId());
        
        return GroupDetailResponse.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .semester(group.getSemester())
                .lecturer(GroupDetailResponse.LecturerInfo.builder()
                        .id(group.getLecturerId())
                        .fullName(lecturerInfo.getDeleted() ? "<Deleted User>" : lecturerInfo.getFullName())
                        .email(lecturerInfo.getDeleted() ? null : lecturerInfo.getEmail())
                        .build())
                .members(members)
                .memberCount(members.size())
                .build();
    }
    
    @Override
    public PageResponse<GroupListResponse> listGroups(int page, int size,
                                                      String semester, UUID lecturerId) {
        log.info("Listing groups: page={}, size={}, semester={}, lecturerId={}", 
                page, size, semester, lecturerId);
        
        PageRequest pageRequest = PageRequest.of(page, size, 
                Sort.by("semester").descending().and(Sort.by("groupName").ascending()));
        
        Page<Group> groupPage = groupRepository.findByFilters(semester, lecturerId, pageRequest);
        
        // Batch fetch all lecturer info
        List<UUID> lecturerIds = groupPage.getContent().stream()
                .map(Group::getLecturerId)
                .distinct()
                .toList();
        
        GetUsersResponse lecturersResponse;
        try {
            lecturersResponse = identityServiceClient.getUsers(lecturerIds);
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when getting lecturers: {}", e.getStatus());
            throw new RuntimeException("Failed to get lecturer info: " + e.getStatus().getCode());
        }
        
        // Build responses with gRPC data
        List<GroupListResponse> groups = groupPage.getContent().stream()
                .map(group -> {
                    long memberCount = userGroupRepository.countAllMembersByGroupId(group.getId());
                    
                    GetUserResponse lecturer = lecturersResponse.getUsersList().stream()
                            .filter(u -> u.getUserId().equals(group.getLecturerId().toString()))
                            .findFirst()
                            .orElse(null);
                    
                    String lecturerName = (lecturer != null && !lecturer.getDeleted()) 
                            ? lecturer.getFullName() 
                            : "<Deleted User>";
                    
                    return GroupListResponse.builder()
                            .id(group.getId())
                            .groupName(group.getGroupName())
                            .semester(group.getSemester())
                            .lecturerName(lecturerName)
                            .memberCount((int) memberCount)
                            .build();
                })
                .collect(Collectors.toList());
        
        return PageResponse.of(
                groups,
                groupPage.getNumber(),
                groupPage.getSize(),
                groupPage.getTotalElements(),
                groupPage.getTotalPages()
        );
    }
    
    @Override
    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request) {
        log.info("Updating group: groupId={}", groupId);
        
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Check if new group name conflicts with existing (excluding self)
        if (!group.getGroupName().equals(request.getGroupName())) {
            if (groupRepository.existsByGroupNameAndSemester(
                    request.getGroupName(), group.getSemester())) {
                throw ConflictException.groupNameDuplicate(
                        request.getGroupName(), group.getSemester());
            }
        }
        
        // Validate new lecturer exists and has LECTURER role via gRPC
        try {
            VerifyUserResponse verification = identityServiceClient.verifyUserExists(
                    request.getLecturerId());
            
            if (!verification.getExists()) {
                throw ResourceNotFoundException.lecturerNotFound(request.getLecturerId());
            }
            
            GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(
                    request.getLecturerId());
            
            if (roleResponse.getRole() != UserRole.LECTURER) {
                throw new ConflictException("INVALID_ROLE", 
                        "User is not a lecturer: " + roleResponse.getRole());
            }
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when validating lecturer: {}", e.getStatus());
            throw new RuntimeException("Failed to validate lecturer: " + e.getStatus().getCode());
        }
        
        // Update fields (semester is immutable)
        group.setGroupName(request.getGroupName());
        group.setLecturerId(request.getLecturerId());
        
        Group savedGroup = groupRepository.save(group);
        log.info("Group updated successfully: groupId={}", groupId);
        
        // Fetch lecturer info for response
        GetUserResponse lecturerInfo = identityServiceClient.getUser(request.getLecturerId());
        
        return GroupResponse.builder()
                .id(savedGroup.getId())
                .groupName(savedGroup.getGroupName())
                .semester(savedGroup.getSemester())
                .lecturerId(savedGroup.getLecturerId())
                .lecturerName(lecturerInfo.getFullName())
                .build();
    }
    
    @Override
    @Transactional
    public void deleteGroup(UUID groupId) {
        log.info("Deleting group (soft): groupId={}", groupId);
        
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Business Rule: Prevent deletion if group has members
        // This follows "least surprise" principle and prevents accidental data loss
        long memberCount = userGroupRepository.countAllMembersByGroupId(groupId);
        if (memberCount > 0) {
            log.warn("Cannot delete group {} - has {} members", groupId, memberCount);
            throw new ConflictException("CANNOT_DELETE_GROUP_WITH_MEMBERS",
                    "Group has " + memberCount + " members. Remove all members first.");
        }
        
        // Soft delete the group (only if empty)
        group.softDelete();
        groupRepository.save(group);
        
        log.info("Group deleted successfully: groupId={}", groupId);
    }
    
    @Override
    @Transactional
    public GroupResponse updateGroupLecturer(UUID groupId, UpdateLecturerRequest request) {
        log.info("UC27 - Updating group lecturer: groupId={}, newLecturerId={}", 
                groupId, request.getLecturerId());
        
        // 1. Validate group exists
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        UUID oldLecturerId = group.getLecturerId();
        
        // 2. Validate new lecturer via gRPC
        try {
            // Check user exists and active
            VerifyUserResponse verification = identityServiceClient.verifyUserExists(
                    request.getLecturerId());
            
            if (!verification.getExists()) {
                throw ResourceNotFoundException.lecturerNotFound(request.getLecturerId());
            }
            
            if (!verification.getActive()) {
                throw new ConflictException("LECTURER_INACTIVE", 
                        "Lecturer account is not active");
            }
            
            // Verify has LECTURER role (BR-UG-011)
            GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(
                    request.getLecturerId());
            
            if (roleResponse.getRole() != UserRole.LECTURER) {
                throw BadRequestException.invalidRole(
                        roleResponse.getRole().name(), "LECTURER");
            }
            
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when validating lecturer: {}", e.getStatus());
            return handleGrpcError(e, "validate lecturer");
        }
        
        // 3. Update lecturer (with audit log)
        if (oldLecturerId.equals(request.getLecturerId())) {
            log.info("UC27 - Lecturer unchanged (idempotent): groupId={}, lecturerId={}", 
                    groupId, oldLecturerId);
        } else {
            log.info("UC27 - Changing lecturer: groupId={}, old={}, new={}", 
                    groupId, oldLecturerId, request.getLecturerId());
        }
        
        group.setLecturerId(request.getLecturerId());
        groupRepository.save(group);
        
        // 4. Fetch new lecturer info for response
        GetUserResponse lecturerInfo = identityServiceClient.getUser(request.getLecturerId());
        
        log.info("UC27 - Group lecturer updated successfully: groupId={}", groupId);
        
        return GroupResponse.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .semester(group.getSemester())
                .lecturerId(group.getLecturerId())
                .lecturerName(lecturerInfo.getFullName())
                .build();
    }
    
    /**
     * Handle gRPC errors with proper HTTP status mapping per API spec.
     */
    private <T> T handleGrpcError(StatusRuntimeException e, String operation) {
        Status.Code code = e.getStatus().getCode();
        
        switch (code) {
            case UNAVAILABLE:
                throw ServiceUnavailableException.identityServiceUnavailable();
            case DEADLINE_EXCEEDED:
                throw GatewayTimeoutException.identityServiceTimeout();
            case NOT_FOUND:
                throw new ResourceNotFoundException("USER_NOT_FOUND", "User not found");
            case PERMISSION_DENIED:
                throw new ForbiddenException("FORBIDDEN", "Access denied");
            case INVALID_ARGUMENT:
                throw new BadRequestException("BAD_REQUEST", "Invalid request");
            default:
                throw new RuntimeException("Failed to " + operation + ": " + code);
        }
    }
}
