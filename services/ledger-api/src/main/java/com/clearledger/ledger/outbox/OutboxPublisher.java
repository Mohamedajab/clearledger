package com.clearledger.ledger.outbox;

import java.util.concurrent.TimeUnit;
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
                kafka.send("payments.lifecycle.v1", event.getAggregateId().toString(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
                event.published();
            } catch (Exception exception) {
                event.failed();
            }
        }
    }
}
