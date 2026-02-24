package com.example.user_groupservice.event;

import com.example.common.events.UserDeletedEvent;
import com.example.user_groupservice.entity.GroupRole;
import com.example.user_groupservice.entity.UserSemesterMembership;
import com.example.user_groupservice.repository.UserSemesterMembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Kafka Consumer for user.deleted events.
 * When a user is deleted from Identity Service, soft delete all their memberships.
 * If deleted user was a LEADER, automatically promote most senior MEMBER.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletedEventConsumer {
    
    private final UserSemesterMembershipRepository membershipRepository;
    
    @KafkaListener(
        topics = "user.deleted",
        groupId = "user-group-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted event: userId={}", event.getUserId());
        
        List<UserSemesterMembership> memberships = 
            membershipRepository.findAllByUserIdIncludingDeleted(event.getUserId());
        
        if (memberships.isEmpty()) {
            log.info("No memberships found for userId={}", event.getUserId());
            return;
        }
        
        // Handle leader reassignment BEFORE soft deletion
        // Process only non-deleted memberships to ensure idempotency
        for (UserSemesterMembership membership : memberships) {
            if (membership.getGroupRole() == GroupRole.LEADER && !membership.isDeleted()) {
                handleLeaderDeletion(membership.getGroupId(), event.getUserId(), event.getDeletedBy());
            }
        }
        
        // Soft delete all memberships (idempotent - only delete if not already deleted)
        memberships.forEach(m -> {
            if (!m.isDeleted()) {
                m.softDelete(event.getDeletedBy());
            }
        });
        membershipRepository.saveAll(memberships);
        
        log.info("Soft deleted {} memberships for userId={}", memberships.size(), event.getUserId());
    }
    
    /**
     * Handle leader deletion by auto-promoting most senior member.
     * Strategy: Promote MEMBER with earliest joinedAt timestamp.
     * If no members exist, group becomes empty (valid state).
     * 
     * @param groupId the group that lost its leader
     * @param deletedUserId the user ID being deleted
     * @param deletedBy the user ID who initiated the deletion
     */
    private void handleLeaderDeletion(Long groupId, Long deletedUserId, Long deletedBy) {
        log.info("Handling leader deletion: groupId={}, deletedUserId={}", groupId, deletedUserId);
        
        // Find all members in the group (excluding deleted user and already deleted memberships)
        List<UserSemesterMembership> members = membershipRepository.findAllByGroupId(groupId)
                .stream()
                .filter(m -> !m.getId().getUserId().equals(deletedUserId))
                .filter(m -> m.getGroupRole() == GroupRole.MEMBER)
                .sorted(Comparator.comparing(UserSemesterMembership::getJoinedAt))
                .toList();
        
        if (!members.isEmpty()) {
            // Auto-promote most senior member (earliest joinedAt)
            UserSemesterMembership newLeader = members.get(0);
            newLeader.promoteToLeader();
            membershipRepository.save(newLeader);
            
            log.info("Auto-promoted user {} to LEADER in group {} after leader deletion", 
                newLeader.getId().getUserId(), groupId);
        } else {
            log.warn("Group {} left without leader - no members to promote (group is now empty)", groupId);
        }
    }
}
