package com.example.user_groupservice.service.impl;

import com.example.user_groupservice.dto.request.CreateGroupRequest;
import com.example.user_groupservice.dto.request.UpdateGroupRequest;
import com.example.user_groupservice.dto.response.GroupDetailResponse;
import com.example.user_groupservice.dto.response.GroupListResponse;
import com.example.user_groupservice.dto.response.GroupResponse;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.entity.Group;
import com.example.user_groupservice.entity.User;
import com.example.user_groupservice.entity.UserGroup;
import com.example.user_groupservice.exception.ConflictException;
import com.example.user_groupservice.exception.ResourceNotFoundException;
import com.example.user_groupservice.repository.GroupRepository;
import com.example.user_groupservice.repository.UserGroupRepository;
import com.example.user_groupservice.repository.UserRepository;
import com.example.user_groupservice.service.GroupService;
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
    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    
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
        
        // Validate lecturer exists and has LECTURER role
        User lecturer = userRepository.findById(request.getLecturerId())
                .orElseThrow(() -> ResourceNotFoundException.lecturerNotFound(
                        request.getLecturerId()));
        
        // Note: In a real system, we would verify the user has LECTURER role
        // via identity-service. For now, we trust the input.
        
        Group group = Group.builder()
                .groupName(request.getGroupName())
                .semester(request.getSemester())
                .lecturer(lecturer)
                .build();
        
        Group savedGroup = groupRepository.save(group);
        log.info("Group created successfully: groupId={}", savedGroup.getId());
        
        return GroupResponse.from(savedGroup);
    }
    
    @Override
    public GroupDetailResponse getGroupById(UUID groupId) {
        log.info("Getting group details: groupId={}", groupId);
        
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Fetch members
        List<UserGroup> memberships = userGroupRepository.findAllByGroupId(groupId);
        
        List<GroupDetailResponse.MemberInfo> members = memberships.stream()
                .map(ug -> GroupDetailResponse.MemberInfo.builder()
                        .userId(ug.getUser().getId())
                        .fullName(ug.getUser().getFullName())
                        .email(ug.getUser().getEmail())
                        .role(ug.getRole().name())
                        .build())
                .collect(Collectors.toList());
        
        return GroupDetailResponse.from(group, members);
    }
    
    @Override
    public PageResponse<GroupListResponse> listGroups(int page, int size,
                                                      String semester, UUID lecturerId) {
        log.info("Listing groups: page={}, size={}, semester={}, lecturerId={}", 
                page, size, semester, lecturerId);
        
        PageRequest pageRequest = PageRequest.of(page, size, 
                Sort.by("semester").descending().and(Sort.by("groupName").ascending()));
        
        Page<Group> groupPage = groupRepository.findByFilters(semester, lecturerId, pageRequest);
        
        List<GroupListResponse> groups = groupPage.getContent().stream()
                .map(group -> {
                    long memberCount = userGroupRepository.countAllMembersByGroupId(group.getId());
                    return GroupListResponse.from(group, (int) memberCount);
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
        
        // Validate new lecturer exists
        User newLecturer = userRepository.findById(request.getLecturerId())
                .orElseThrow(() -> ResourceNotFoundException.lecturerNotFound(
                        request.getLecturerId()));
        
        // Update fields (semester is immutable)
        group.setGroupName(request.getGroupName());
        group.setLecturer(newLecturer);
        
        Group savedGroup = groupRepository.save(group);
        log.info("Group updated successfully: groupId={}", groupId);
        
        return GroupResponse.from(savedGroup);
    }
    
    @Override
    @Transactional
    public void deleteGroup(UUID groupId) {
        log.info("Deleting group (soft): groupId={}", groupId);
        
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Soft delete the group
        group.softDelete();
        groupRepository.save(group);
        
        // Also soft delete all memberships
        List<UserGroup> memberships = userGroupRepository.findAllByGroupId(groupId);
        memberships.forEach(UserGroup::softDelete);
        userGroupRepository.saveAll(memberships);
        
        log.info("Group deleted successfully: groupId={}", groupId);
    }
}
