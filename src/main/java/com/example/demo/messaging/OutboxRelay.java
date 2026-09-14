package com.example.demo.messaging;

import com.example.demo.entity.OutboxEvent;
import com.example.demo.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes pending outbox rows to Kafka. Each row is sent and acknowledged (acks=all) before it
 * is stamped {@code published_at}; a crash in between republishes it on the next run — delivery is
 * at-least-once and consumers deduplicate on the {@code eventId} header. A failed send stops the
 * batch so rows keep their order, records the failure on the row, and is retried on the next poll.
 * <p>
 * Runs only when {@code app.outbox.relay-enabled=true} (the dev profile defaults it off).
 */
@Component
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true")
@Slf4j
public class OutboxRelay {

    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";
    public static final String HEADER_AGGREGATE_TYPE = "aggregateType";

    private final OutboxRepository outboxRepository;
    private final KafkaOperations<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxRepository outboxRepository,
                       KafkaOperations<String, String> kafkaTemplate,
                       @Value("${app.kafka.topics.trade-executed}") String topic,
                       @Value("${app.outbox.batch-size:100}") int batchSize,
                       @Value("${app.outbox.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = Math.max(1, batchSize);
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:1000}")
    public void poll() {
        int published = publishPending();
        if (published > 0) {
            log.info("Outbox relay published {} event(s) to {}", published, topic);
        }
    }

    /** Publishes the next batch of pending events in order. Returns how many were published. */
    public int publishPending() {
        List<OutboxEvent> pending = outboxRepository.findUnpublished(PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return 0;
        }

        List<OutboxEvent> touched = new ArrayList<>();
        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(toRecord(event)).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                event.setPublishedAt(LocalDateTime.now());
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordFailure(event, e);
                touched.add(event);
                break;
            } catch (Exception e) {
                recordFailure(event, e);
                touched.add(event);
                break;   // keep order: retry this one first on the next poll
            }
            touched.add(event);
        }
        outboxRepository.saveAll(touched);
        return published;
    }

    private void recordFailure(OutboxEvent event, Exception e) {
        event.setAttempts(event.getAttempts() + 1);
        String message = e.getCause() != null ? e.getCause().toString() : e.toString();
        event.setLastError(message.length() > 512 ? message.substring(0, 512) : message);
        log.warn("Outbox event {} (attempt {}) not published: {}", event.getEventId(), event.getAttempts(), message);
    }

    ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getMessageKey(), event.getPayload());
        record.headers()
                .add(HEADER_EVENT_ID, event.getEventId().getBytes(StandardCharsets.UTF_8))
                .add(HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8))
                .add(HEADER_AGGREGATE_TYPE, event.getAggregateType().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
