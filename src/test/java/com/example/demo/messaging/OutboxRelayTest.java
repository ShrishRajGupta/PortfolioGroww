package com.example.demo.messaging;

import com.example.demo.entity.OutboxEvent;
import com.example.demo.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private KafkaOperations<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        relay = new OutboxRelay(outboxRepository, kafkaTemplate, "trade-executed", 50, 1000);
    }

    private static OutboxEvent event(long id, String eventId) {
        return OutboxEvent.builder().id(id).eventId(eventId).aggregateType("Trade").aggregateId(id)
                .eventType("TradeExecuted").messageKey("7").payload("{\"eventId\":\"" + eventId + "\"}").attempts(0).build();
    }

    private static CompletableFuture<SendResult<String, String>> ok() {
        return CompletableFuture.completedFuture(null);
    }

    @Test
    void publishesPendingInOrder_marksThemPublished_withHeaders() {
        OutboxEvent a = event(1, "e-1");
        OutboxEvent b = event(2, "e-2");
        when(outboxRepository.findUnpublished(any(Pageable.class))).thenReturn(List.of(a, b));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(ok());

        int published = relay.publishPending();

        assertEquals(2, published);
        assertNotNull(a.getPublishedAt());
        assertNotNull(b.getPublishedAt());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(sent.capture());
        ProducerRecord<String, String> first = sent.getAllValues().get(0);
        assertEquals("trade-executed", first.topic());
        assertEquals("7", first.key());
        assertEquals(a.getPayload(), first.value());
        assertEquals("e-1", new String(first.headers().lastHeader(OutboxRelay.HEADER_EVENT_ID).value(), StandardCharsets.UTF_8));
        assertEquals("TradeExecuted", new String(first.headers().lastHeader(OutboxRelay.HEADER_EVENT_TYPE).value(), StandardCharsets.UTF_8));
        assertEquals("e-2", new String(sent.getAllValues().get(1).headers().lastHeader(OutboxRelay.HEADER_EVENT_ID).value(), StandardCharsets.UTF_8));
        verify(outboxRepository).saveAll(List.of(a, b));
    }

    @Test
    void failedSend_stopsTheBatch_recordsAttempt_leavesRowPending() {
        OutboxEvent a = event(1, "e-1");
        OutboxEvent b = event(2, "e-2");
        when(outboxRepository.findUnpublished(any(Pageable.class))).thenReturn(List.of(a, b));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker away")));

        int published = relay.publishPending();

        assertEquals(0, published);
        assertNull(a.getPublishedAt());
        assertEquals(1, a.getAttempts());
        assertTrue(a.getLastError().contains("broker away"));
        assertEquals(0, b.getAttempts(), "later rows are not attempted, so order is preserved");
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        verify(outboxRepository).saveAll(List.of(a));
    }

    @Test
    void partialFailure_publishesTheHead_stopsAtTheFailure() {
        OutboxEvent a = event(1, "e-1");
        OutboxEvent b = event(2, "e-2");
        OutboxEvent c = event(3, "e-3");
        when(outboxRepository.findUnpublished(any(Pageable.class))).thenReturn(List.of(a, b, c));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(ok())
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        int published = relay.publishPending();

        assertEquals(1, published);
        assertNotNull(a.getPublishedAt());
        assertNull(b.getPublishedAt());
        assertEquals(1, b.getAttempts());
        assertNull(c.getPublishedAt());
        assertEquals(0, c.getAttempts());
        verify(outboxRepository).saveAll(List.of(a, b));
    }

    @Test
    void nothingPending_doesNothing() {
        when(outboxRepository.findUnpublished(any(Pageable.class))).thenReturn(List.of());

        assertEquals(0, relay.publishPending());
        verifyNoInteractions(kafkaTemplate);
        verify(outboxRepository, never()).saveAll(anyList());
    }
}
