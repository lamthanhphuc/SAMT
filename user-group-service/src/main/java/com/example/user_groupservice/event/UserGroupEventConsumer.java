package com.example.user_groupservice.event;

import com.example.common.events.UserDeletedEvent;
import com.example.user_groupservice.entity.UserSemesterMembership;
import com.example.user_groupservice.repository.UserSemesterMembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Event Consumer cho User-Group Service
 * 
 * Consume events:
 * - UserDeletedEvent: Remove user từ tất cả groups khi user bị hard delete
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserGroupEventConsumer {

    private final UserSemesterMembershipRepository membershipRepository;

    /**
     * Handle UserDeletedEvent
     * 
     * Actions:
     * 1. Find all memberships của user
     * 2. Hard delete (remove) tất cả memberships
     * 3. Log audit trail
     * 
     * Note: Không dùng soft delete vì user đã bị hard delete
     */
    @KafkaListener(
            topics = "user.deleted",
            groupId = "user-group-service",
            containerFactory = "userDeletedEventKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleUserDeletedEvent(UserDeletedEvent event) {
        log.info("Received UserDeletedEvent: userId={}, email={}", 
                 event.getUserId(), event.getEmail());

        try {
            Long userId = event.getUserId();

            // Find all memberships của user (including soft deleted)
            List<UserSemesterMembership> memberships = 
                    membershipRepository.findAllByUserIdIncludingDeleted(userId);

            if (memberships.isEmpty()) {
                log.info("No memberships found for user: {}", userId);
                return;
            }

            log.info("Found {} membership(s) for user: {}", memberships.size(), userId);

            // Hard delete all memberships
            membershipRepository.deleteAll(memberships);

            log.info("Successfully removed {} membership(s) for deleted user: {}", 
                     memberships.size(), userId);

            // TODO: Audit log (optional)
            // auditLogService.logUserMembershipCleanup(event);

        } catch (Exception e) {
            log.error("Error handling UserDeletedEvent: userId={}", 
                      event.getUserId(), e);
            // Throw exception để Kafka retry
            throw new RuntimeException("Failed to handle UserDeletedEvent", e);
        }
    }
}
