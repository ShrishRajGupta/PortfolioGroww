package com.example.demo.messaging;

import com.example.demo.event.TradeExecutedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downstream subscriber to TradeExecuted events. Demonstrates the consumer side of at-least-once
 * delivery: every event is deduplicated on its {@code eventId} before any work happens.
 * <p>
 * The seen-set is in memory, which is enough to prove the contract here; a real consumer persists
 * the processed eventId together with its side effect in one transaction so deduplication survives
 * restarts. Starts only when {@code app.kafka.consumer-enabled=true}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TradeExecutedConsumer {

    private final ObjectMapper objectMapper;
    private final Set<String> seenEventIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();

    @KafkaListener(topics = "${app.kafka.topics.trade-executed}",
            groupId = "${app.kafka.consumer-group}",
            autoStartup = "${app.kafka.consumer-enabled}")
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventId = eventIdOf(record);
        if (eventId == null) {
            log.warn("TradeExecuted message without eventId at {}-{}@{} skipped", record.topic(), record.partition(), record.offset());
            return;
        }
        if (!seenEventIds.add(eventId)) {
            duplicates.incrementAndGet();
            log.info("Duplicate TradeExecuted {} ignored", eventId);
            return;
        }
        TradeExecutedEvent event;
        try {
            event = objectMapper.readValue(record.value(), TradeExecutedEvent.class);
        } catch (IOException e) {
            seenEventIds.remove(eventId);
            throw new IllegalArgumentException("Unreadable TradeExecuted payload for event " + eventId, e);
        }
        processed.incrementAndGet();
        log.info("TradeExecuted trade={} user={} {} {} x {} @ {}", event.tradeId(), event.userAccountId(),
                event.tradeType(), event.stockId(), event.quantity(), event.price());
    }

    private String eventIdOf(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(OutboxRelay.HEADER_EVENT_ID);
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        try {
            return objectMapper.readTree(record.value()).path("eventId").asText(null);
        } catch (IOException e) {
            return null;
        }
    }

    public long processedCount() {
        return processed.get();
    }

    public long duplicateCount() {
        return duplicates.get();
    }
}
