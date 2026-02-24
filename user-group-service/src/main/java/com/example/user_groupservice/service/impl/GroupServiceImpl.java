package com.example.user_groupservice.service.impl;

import com.example.user_groupservice.dto.request.CreateGroupRequest;
import com.example.user_groupservice.dto.request.UpdateGroupRequest;
import com.example.user_groupservice.dto.request.UpdateLecturerRequest;
import com.example.user_groupservice.dto.response.GroupDetailResponse;
import com.example.user_groupservice.dto.response.GroupListResponse;
import com.example.user_groupservice.dto.response.GroupResponse;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.entity.Group;
import com.example.user_groupservice.entity.Semester;
import com.example.user_groupservice.entity.UserSemesterMembership;
import com.example.user_groupservice.exception.*;
import com.example.user_groupservice.grpc.*;
import com.example.user_groupservice.repository.GroupRepository;
import com.example.user_groupservice.repository.SemesterRepository;
import com.example.user_groupservice.repository.UserSemesterMembershipRepository;
import com.example.user_groupservice.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final UserSemesterMembershipRepository membershipRepository;
    private final SemesterRepository semesterRepository;
    private final IdentityServiceClient identityServiceClient;
    private final GrpcExceptionHandler grpcExceptionHandler;
    
    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        log.info("Creating group: groupName={}, semesterId={}", 
                request.getGroupName(), request.getSemesterId());
        
        // Check group name uniqueness in semester
        if (groupRepository.existsByGroupNameAndSemesterId(
                request.getGroupName(), request.getSemesterId())) {
            throw ConflictException.groupNameDuplicate(
                    request.getGroupName(), request.getSemesterId().toString());
        }
        
        // Validate lecturer exists and has LECTURER role via gRPC
        VerifyUserResponse verification = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.verifyUserExists(request.getLecturerId()),
                "verifyLecturerExists[createGroup]");
        
        if (!verification.getExists()) {
            throw ResourceNotFoundException.lecturerNotFound(request.getLecturerId());
        }
        
        if (!verification.getActive()) {
            throw new ConflictException("LECTURER_INACTIVE", 
                    "Lecturer account is not active");
        }
        
        // Verify LECTURER role
        GetUserRoleResponse roleResponse = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.getUserRole(request.getLecturerId()),
                "getUserRole[createGroup]");
        
        if (roleResponse.getRole() != UserRole.LECTURER) {
            throw new ConflictException("INVALID_ROLE", 
                    "User is not a lecturer: " + roleResponse.getRole());
        }
        
        Group group = Group.builder()
                .groupName(request.getGroupName())
                .semesterId(request.getSemesterId())
                .lecturerId(request.getLecturerId())
                .build();
        
        Group savedGroup = groupRepository.save(group);
        log.info("Group created successfully: groupId={}", savedGroup.getId());
        
        // Fetch lecturer info for response
        GetUserResponse lecturerInfo = identityServiceClient.getUser(request.getLecturerId());
        
        // Resolve semester code from local database
        String semesterCode = resolveSemesterCode(savedGroup.getSemesterId());
        
        return GroupResponse.builder()
                .id(savedGroup.getId())
                .groupName(savedGroup.getGroupName())
                .semesterId(savedGroup.getSemesterId())
                .semesterCode(semesterCode)
                .lecturerId(savedGroup.getLecturerId())
                .lecturerName(lecturerInfo.getFullName())
                .build();
    }
    
    @Override
    public GroupDetailResponse getGroupById(Long groupId) {
        log.info("Getting group details: groupId={}", groupId);
        
        Group group = groupRepository.findByIdAndNotDeleted(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Fetch members
        List<UserSemesterMembership> memberships = membershipRepository.findAllByGroupId(groupId);
        
        // Batch fetch user info from Identity Service (avoid N+1 calls)
        List<Long> userIds = memberships.stream()
                .map(m -> m.getId().getUserId())
                .toList();
        
        GetUsersResponse usersResponse = identityServiceClient.getUsers(userIds);
        
        // Map user info to member list
        List<GroupDetailResponse.MemberInfo> members = memberships.stream()
                .map(m -> {
                    GetUserResponse userInfo = usersResponse.getUsersList().stream()
                            .filter(u -> Long.parseLong(u.getUserId()) == m.getId().getUserId())
                            .findFirst()
                            .orElse(null);
                    
                    return GroupDetailResponse.MemberInfo.builder()
                            .userId(m.getId().getUserId())
                            .fullName(userInfo != null ? userInfo.getFullName() : "<Deleted User>")
                            .email(userInfo != null && !userInfo.getDeleted() ? userInfo.getEmail() : null)
                            .role(m.getGroupRole().name())
                            .build();
                })
                .collect(Collectors.toList());
        
        // Fetch lecturer info
        GetUserResponse lecturerInfo = identityServiceClient.getUser(group.getLecturerId());
        
        // Resolve semester code from local database
        String semesterCode = resolveSemesterCode(group.getSemesterId());
        
        return GroupDetailResponse.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .semesterId(group.getSemesterId())
                .semesterCode(semesterCode)
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
                                                      Long semesterId, Long lecturerId) {
        log.info("Listing groups: page={}, size={}, semesterId={}, lecturerId={}", 
                page, size, semesterId, lecturerId);
        
        PageRequest pageRequest = PageRequest.of(page, size, 
                Sort.by("semesterId").descending().and(Sort.by("groupName").ascending()));
        
        Page<Group> groupPage = groupRepository.findByFilters(semesterId, lecturerId, pageRequest);
        
        // Batch fetch all lecturer info
        List<Long> lecturerIds = groupPage.getContent().stream()
                .map(Group::getLecturerId)
                .distinct()
                .toList();
        
        GetUsersResponse lecturersResponse = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.getUsers(lecturerIds),
                "getUsers[listGroups]");
        
        // Batch fetch member counts for all groups (prevents N+1 query)
        List<Long> groupIds = groupPage.getContent().stream()
                .map(Group::getId)
                .toList();
        
        var memberCounts = membershipRepository.countMembersByGroupIds(groupIds)
                .stream()
                .collect(Collectors.toMap(
                        com.example.user_groupservice.repository.UserSemesterMembershipRepository.GroupMemberCount::getGroupId,
                        com.example.user_groupservice.repository.UserSemesterMembershipRepository.GroupMemberCount::getMemberCount
                ));
        
        // Build responses with gRPC data
        List<GroupListResponse> groups = groupPage.getContent().stream()
                .map(group -> {
                    long memberCount = memberCounts.getOrDefault(group.getId(), 0L);
                    
                    GetUserResponse lecturer = lecturersResponse.getUsersList().stream()
                            .filter(u -> Long.parseLong(u.getUserId()) == group.getLecturerId())
                            .findFirst()
                            .orElse(null);
                    
                    String lecturerName = (lecturer != null && !lecturer.getDeleted()) 
                            ? lecturer.getFullName() 
                            : "<Deleted User>";
                    
                    // Resolve semester code from local database
                    String semesterCode = resolveSemesterCode(group.getSemesterId());
                    
                    return GroupListResponse.builder()
                            .id(group.getId())
                            .groupName(group.getGroupName())
                            .semesterId(group.getSemesterId())
                            .semesterCode(semesterCode)
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
    public GroupResponse updateGroup(Long groupId, UpdateGroupRequest request) {
        log.info("Updating group: groupId={}", groupId);
        
        Group group = groupRepository.findByIdAndNotDeleted(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Check if new group name conflicts with existing (excluding self)
        if (!group.getGroupName().equals(request.getGroupName())) {
            if (groupRepository.existsByGroupNameAndSemesterId(
                    request.getGroupName(), group.getSemesterId())) {
                throw ConflictException.groupNameDuplicate(
                        request.getGroupName(), group.getSemesterId().toString());
            }
        }
        
        // Validate new lecturer exists and has LECTURER role via gRPC
        VerifyUserResponse verification = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.verifyUserExists(request.getLecturerId()),
                "verifyLecturerExists[updateGroup]");
        
        if (!verification.getExists()) {
            throw ResourceNotFoundException.lecturerNotFound(request.getLecturerId());
        }
        
        GetUserRoleResponse roleResponse = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.getUserRole(request.getLecturerId()),
                "getUserRole[updateGroup]");
        
        if (roleResponse.getRole() != UserRole.LECTURER) {
            throw new ConflictException("INVALID_ROLE", 
                    "User is not a lecturer: " + roleResponse.getRole());
        }
        
        // Update fields (semester is immutable)
        group.setGroupName(request.getGroupName());
        group.setLecturerId(request.getLecturerId());
        
        Group savedGroup = groupRepository.save(group);
        log.info("Group updated successfully: groupId={}", groupId);
        
        // Fetch lecturer info for response
        GetUserResponse lecturerInfo = identityServiceClient.getUser(request.getLecturerId());
        
        // Resolve semester code from local database
        String semesterCode = resolveSemesterCode(savedGroup.getSemesterId());
        
        return GroupResponse.builder()
                .id(savedGroup.getId())
                .groupName(savedGroup.getGroupName())
                .semesterId(savedGroup.getSemesterId())
                .semesterCode(semesterCode)
                .lecturerId(savedGroup.getLecturerId())
                .lecturerName(lecturerInfo.getFullName())
                .build();
    }
    
    @Override
    @Transactional
    public void deleteGroup(Long groupId, Long deletedByUserId) {
        log.info("Deleting group (soft): groupId={}, deletedBy={}", groupId, deletedByUserId);
        
        Group group = groupRepository.findByIdAndNotDeleted(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Business Rule: Prevent deletion if group has members (INCLUDING LEADER)
        // This follows "least surprise" principle and prevents accidental data loss
        long memberCount = membershipRepository.countAllMembersByGroupId(groupId);
        if (memberCount > 0) {
            log.warn("Cannot delete group {} - has {} members", groupId, memberCount);
            throw new ConflictException("CANNOT_DELETE_GROUP_WITH_MEMBERS",
                    "Group has " + memberCount + " members. Remove all members first.");
        }
        
        // Soft delete the group (only if empty)
        group.softDelete(deletedByUserId);
        groupRepository.save(group);
        
        log.info("Group deleted successfully: groupId={}, deletedBy={}", groupId, deletedByUserId);
    }
    
    @Override
    @Transactional
    public GroupResponse updateGroupLecturer(Long groupId, UpdateLecturerRequest request) {
        log.info("UC27 - Updating group lecturer: groupId={}, newLecturerId={}", 
                groupId, request.getLecturerId());
        
        // 1. Validate group exists
        Group group = groupRepository.findByIdAndNotDeleted(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        Long oldLecturerId = group.getLecturerId();
        
        // 2. Validate new lecturer via gRPC
        // Check user exists and active
        VerifyUserResponse verification = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.verifyUserExists(request.getLecturerId()),
                "verifyLecturerExists[updateLecturer]");
        
        if (!verification.getExists()) {
            throw ResourceNotFoundException.lecturerNotFound(request.getLecturerId());
        }
        
        if (!verification.getActive()) {
            throw new ConflictException("LECTURER_INACTIVE", 
                    "Lecturer account is not active");
        }
        
        // Verify has LECTURER role (BR-UG-011)
        GetUserRoleResponse roleResponse = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.getUserRole(request.getLecturerId()),
                "getUserRole[updateLecturer]");
        
        if (roleResponse.getRole() != UserRole.LECTURER) {
            throw BadRequestException.invalidRole(
                    roleResponse.getRole().name(), "LECTURER");
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
        
        // Resolve semester code from local database
        String semesterCode = resolveSemesterCode(group.getSemesterId());
        
        return GroupResponse.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .semesterId(group.getSemesterId())
                .semesterCode(semesterCode)
                .lecturerId(group.getLecturerId())
                .lecturerName(lecturerInfo.getFullName())
                .build();
    }
    
    /**
     * Resolve semester code from local database.
     * 
     * @param semesterId Semester ID
     * @return Semester code (e.g., "SPRING2025")
     * @throws ResourceNotFoundException if semester not found
     */
    private String resolveSemesterCode(Long semesterId) {
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> ResourceNotFoundException.semesterNotFound(semesterId));
        return semester.getSemesterCode();
    }
    
}
