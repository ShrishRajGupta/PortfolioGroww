package com.example.demo.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TradeExecutedConsumerTest {

    private final TradeExecutedConsumer consumer =
            new TradeExecutedConsumer(new ObjectMapper().registerModule(new JavaTimeModule()));

    private static final String PAYLOAD = """
            {"eventId":"%s","tradeId":42,"clientTradeId":null,"userAccountId":7,"stockId":3,
             "tradeType":"BUY","quantity":2,"price":101.2500,"occurredAt":"2026-09-13T12:00:00"}
            """;

    private static ConsumerRecord<String, String> record(String headerEventId, String payloadEventId, long offset) {
        ConsumerRecord<String, String> r = new ConsumerRecord<>("trade-executed", 0, offset, "7", PAYLOAD.formatted(payloadEventId));
        if (headerEventId != null) {
            r.headers().add(new RecordHeader(OutboxRelay.HEADER_EVENT_ID, headerEventId.getBytes(StandardCharsets.UTF_8)));
        }
        return r;
    }

    @Test
    void processesAnEventOnce_andIgnoresRedeliveries() {
        consumer.onMessage(record("e-1", "e-1", 0));
        consumer.onMessage(record("e-1", "e-1", 0));   // at-least-once redelivery
        consumer.onMessage(record("e-1", "e-1", 5));   // same event, different offset

        assertEquals(1, consumer.processedCount());
        assertEquals(2, consumer.duplicateCount());
    }

    @Test
    void distinctEvents_areAllProcessed() {
        consumer.onMessage(record("e-1", "e-1", 0));
        consumer.onMessage(record("e-2", "e-2", 1));

        assertEquals(2, consumer.processedCount());
        assertEquals(0, consumer.duplicateCount());
    }

    @Test
    void missingHeader_fallsBackToTheEventIdInThePayload() {
        consumer.onMessage(record(null, "e-9", 0));
        consumer.onMessage(record(null, "e-9", 1));

        assertEquals(1, consumer.processedCount());
        assertEquals(1, consumer.duplicateCount());
    }

    @Test
    void unreadablePayload_isRejected_andNotRememberedAsProcessed() {
        ConsumerRecord<String, String> bad = new ConsumerRecord<>("trade-executed", 0, 0, "7", "{not json");
        bad.headers().add(new RecordHeader(OutboxRelay.HEADER_EVENT_ID, "e-bad".getBytes(StandardCharsets.UTF_8)));

        assertThrows(IllegalArgumentException.class, () -> consumer.onMessage(bad));
        assertEquals(0, consumer.processedCount());

        // a corrected redelivery of the same event is then processed, not treated as a duplicate
        consumer.onMessage(record("e-bad", "e-bad", 1));
        assertEquals(1, consumer.processedCount());
        assertEquals(0, consumer.duplicateCount());
    }
}
