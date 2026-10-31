package com.clearledger.ledger.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxPublisherTest {
    private final OutboxEventRepository events = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxPublisher publisher = new OutboxPublisher(events, kafka, 100);
    private final OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "payment.completed.v1", "{\"amountMinor\":1250}");

    private void pending() {
        when(events.findByStatusOrderByCreatedAtAsc(eq(OutboxEvent.Status.PENDING), any(Pageable.class)))
            .thenAnswer(invocation -> event.getStatus() == OutboxEvent.Status.PENDING ? List.of(event) : List.of());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void failedAcknowledgementRemainsPendingThenRetriesWithTheSameEventId() {
        pending();
        when(kafka.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
            .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        publisher.publishPending();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
        assertThat(event.getAttempts()).isEqualTo(2);

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, times(2)).send(sent.capture());
        for (ProducerRecord<String, String> record : sent.getAllValues()) {
            assertThat(record.key()).isEqualTo(event.getAggregateId().toString());
            assertThat(record.value()).isEqualTo(event.getPayload());
            assertThat(new String(record.headers().lastHeader("event-id").value(), StandardCharsets.UTF_8))
                .isEqualTo(event.getId().toString());
        }
        publisher.publishPending();
        verifyNoMoreInteractions(kafka);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tenFailedAttemptsExhaustTheExistingPolicy() {
        pending();
        when(kafka.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        for (int attempt = 0; attempt < 11; attempt++) publisher.publishPending();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(event.getAttempts()).isEqualTo(10);
        verify(kafka, times(10)).send(any(ProducerRecord.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void acknowledgementTimeoutLeavesTheEventPending() throws Exception {
        pending();
        CompletableFuture<SendResult<String, String>> timedOut = mock(CompletableFuture.class);
        when(timedOut.get(5, TimeUnit.SECONDS)).thenThrow(new java.util.concurrent.TimeoutException());
        when(kafka.send(any(ProducerRecord.class))).thenReturn(timedOut);
        publisher.publishPending();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptionPreservesTheFlagAndStopsTheBatchWithoutLosingPendingWork() throws Exception {
        OutboxEvent later = new OutboxEvent(UUID.randomUUID(), "payment.completed.v1", "{}");
        when(events.findByStatusOrderByCreatedAtAsc(eq(OutboxEvent.Status.PENDING), any(Pageable.class)))
            .thenReturn(List.of(event, later));
        CompletableFuture<SendResult<String, String>> interrupted = mock(CompletableFuture.class);
        when(interrupted.get(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        when(kafka.send(any(ProducerRecord.class))).thenReturn(interrupted);
        try {
            publisher.publishPending();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
            assertThat(later.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
            assertThat(event.getAttempts()).isZero();
            verify(kafka).send(any(ProducerRecord.class));
        } finally {
            Thread.interrupted();
        }
    }
}
