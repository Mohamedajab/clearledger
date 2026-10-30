package com.clearledger.ledger.outbox;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "clearledger.outbox.publisher.enabled", havingValue = "true")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository events;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository events, KafkaTemplate<String, String> kafka,
                           @Value("${clearledger.outbox.publisher.batch-size:100}") int batchSize) {
        this.events = events; this.kafka = kafka; this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${clearledger.outbox.publisher.interval-ms:1000}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : events.findByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING, PageRequest.of(0, batchSize))) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    "payments.lifecycle.v1", event.getAggregateId().toString(), event.getPayload());
                record.headers().add("event-id", event.getId().toString().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(5, TimeUnit.SECONDS);
                event.published();
            } catch (InterruptedException exception) {
                // Delivery may have reached the broker; leave it pending for safe replay.
                Thread.currentThread().interrupt();
                log.info("Outbox publication interrupted eventId={}", event.getId());
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                event.failed();
                log.warn("Outbox publication failed eventId={} attempt={} status={} cause={}",
                    event.getId(), event.getAttempts(), event.getStatus(), exception.getClass().getSimpleName());
            }
        }
    }
}
