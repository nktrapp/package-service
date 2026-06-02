package br.furb.pkg.infrastructure.messaging.outbox;

import br.furb.pkg.core.port.EventPublisherPort;
import br.furb.pkg.domain.port.OutboxRepository;
import br.furb.pkg.domain.port.OutboxRepository.OutboxEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayScheduler")
class OutboxRelaySchedulerTest {

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    EventPublisherPort eventPublisherPort;

    @Nested
    @DisplayName("Relay")
    class Relay {

        @Test
        @DisplayName("Given a pending event, should publish to the FIFO queue and mark it as published when relay succeeds")
        void shouldPublishAndMarkAsPublishedWhenRelaySucceeds() {
            // GIVEN
            OutboxRelayScheduler scheduler = buildScheduler();
            OutboxEntry entry = new OutboxEntry(
                    "event-1",
                    "package.created",
                    "{\"packageId\":\"pkg-1\"}",
                    "pkg-1",
                    Instant.parse("2026-05-31T10:00:00Z"),
                    0
            );
            when(outboxRepository.claimPending(eq(10), argThat(instant -> nonNull(instant)), argThat(instant -> nonNull(instant))))
                    .thenReturn(List.of(entry));

            // WHEN
            scheduler.relay();

            // THEN — group id is the packageId, dedup id is the eventId
            ArgumentCaptor<String> envelopeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Instant> publishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(eventPublisherPort).publish(eq("package-events-queue"), envelopeCaptor.capture(), eq("pkg-1"), eq("event-1"));
            verify(outboxRepository).markAsPublished(eq("event-1"), publishedAtCaptor.capture());
            verify(outboxRepository, never()).markForRetry(any(), any(), any(Instant.class), anyInt());

            assertThat(envelopeCaptor.getValue())
                    .contains("\"eventId\":\"event-1\"")
                    .contains("\"source\":\"package-service\"");
            assertThat(publishedAtCaptor.getValue()).isNotNull();
        }

        @Test
        @DisplayName("Given a publishing failure, should schedule retry when attempts are still available")
        void shouldScheduleRetryWhenPublishingFails() {
            // GIVEN
            OutboxRelayScheduler scheduler = buildScheduler();
            OutboxEntry entry = new OutboxEntry(
                    "event-1",
                    "package.created",
                    "{\"packageId\":\"pkg-1\"}",
                    "pkg-1",
                    Instant.parse("2026-05-31T10:00:00Z"),
                    1
            );
            when(outboxRepository.claimPending(eq(10), argThat(instant -> nonNull(instant)), argThat(instant -> nonNull(instant))))
                    .thenReturn(List.of(entry));
            when(outboxRepository.markForRetry(eq("event-1"), eq("boom"), argThat(instant -> nonNull(instant)), eq(5)))
                    .thenReturn(new OutboxRepository.RetryOutcome(true, 2));
            doThrow(new RuntimeException("boom"))
                    .when(eventPublisherPort)
                    .publish(eq("package-events-queue"), argThat(message -> message.contains("event-1")), eq("pkg-1"), eq("event-1"));

            // WHEN
            scheduler.relay();

            // THEN
            verify(outboxRepository).markForRetry(eq("event-1"), eq("boom"), argThat(instant -> nonNull(instant)), eq(5));
            verify(outboxRepository, never()).markAsPublished(eq("event-1"), argThat(instant -> nonNull(instant)));
        }
    }

    private OutboxRelayScheduler buildScheduler() {
        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(outboxRepository, eventPublisherPort, new ObjectMapper());
        ReflectionTestUtils.setField(scheduler, "outboundQueue", "package-events-queue");
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 5);
        ReflectionTestUtils.setField(scheduler, "retryDelayMs", 5000L);
        ReflectionTestUtils.setField(scheduler, "processingTimeoutMs", 60000L);
        return scheduler;
    }
}
