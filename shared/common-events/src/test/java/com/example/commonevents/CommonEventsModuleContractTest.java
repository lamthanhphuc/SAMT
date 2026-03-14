package com.example.commonevents;

import com.example.common.events.UserDeletedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CommonEventsModuleContractTest {

    @Test
    void exposesSharedUserDeletedEventType() {
        Instant now = Instant.now();
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId(100L)
                .email("user@samt.local")
                .role("STUDENT")
                .deletedAt(now)
                .deletedBy(1L)
                .eventId("evt-1")
                .eventTimestamp(now)
                .build();

        assertThat(event.getUserId()).isEqualTo(100L);
        assertThat(event.getEmail()).isEqualTo("user@samt.local");
        assertThat(event.getRole()).isEqualTo("STUDENT");
        assertThat(event.getDeletedAt()).isEqualTo(now);
        assertThat(event.getDeletedBy()).isEqualTo(1L);
        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getEventTimestamp()).isEqualTo(now);
    }
}
