package com.example.identityservice.event;

import com.example.common.events.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Event Publisher cho Identity Service
 * 
 * Publish events:
 * - UserDeletedEvent: Khi user bị hard delete
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityEventPublisher {

    private final KafkaTemplate<String, UserDeletedEvent> kafkaTemplate;

    private static final String USER_DELETED_TOPIC = "user.deleted";

    /**
     * Publish UserDeletedEvent
     * 
     * @param userId User ID bị delete
     * @param email User email
     * @param role User role
     * @param deletedBy Admin ID thực hiện delete
     */
    public void publishUserDeletedEvent(Long userId, String email, String role, Long deletedBy) {
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId(userId)
                .email(email)
                .role(role)
                .deletedAt(Instant.now())
                .deletedBy(deletedBy)
                .eventId(UUID.randomUUID().toString())
                .eventTimestamp(Instant.now())
                .build();

        log.info("Publishing UserDeletedEvent: userId={}, email={}", userId, email);

        try {
            kafkaTemplate.send(USER_DELETED_TOPIC, userId.toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("UserDeletedEvent published successfully: userId={}", userId);
                        } else {
                            log.error("Failed to publish UserDeletedEvent: userId={}", userId, ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing UserDeletedEvent: userId={}", userId, e);
            // Don't throw exception - event publishing failure should not block delete operation
        }
    }
}
