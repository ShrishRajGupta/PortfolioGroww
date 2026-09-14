package com.example.demo.serviceTesting;

import com.example.demo.entity.OutboxEvent;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.event.TradeExecutedEvent;
import com.example.demo.repository.OutboxRepository;
import com.example.demo.service.Impl.OutboxServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OutboxServiceImplTest {

    @Mock
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        outboxService = new OutboxServiceImpl(outboxRepository, objectMapper);
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recordTradeExecuted_buildsAPublishableEvent() throws Exception {
        UserAccount user = new UserAccount(7L, "U", "u@example.com", null);
        Stock stock = new Stock(3L, "ACME", BigDecimal.ONE, new BigDecimal("120.5000"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        Trade trade = Trade.builder().id(42L).clientTradeId("c-1").userAccount(user).stock(stock)
                .tradeType(TradeType.SELL).quantity(4).price(new BigDecimal("120.5000"))
                .createdAt(LocalDateTime.of(2026, 9, 13, 12, 0)).build();

        OutboxEvent row = outboxService.recordTradeExecuted(trade);

        assertEquals("Trade", row.getAggregateType());
        assertEquals(42L, row.getAggregateId());
        assertEquals("TradeExecuted", row.getEventType());
        assertEquals("7", row.getMessageKey(), "keyed by user so a user's events stay ordered");
        assertNull(row.getPublishedAt());
        assertEquals(0, row.getAttempts());
        assertNotNull(row.getEventId());
        assertEquals(36, row.getEventId().length(), "UUID");

        TradeExecutedEvent payload = objectMapper.readValue(row.getPayload(), TradeExecutedEvent.class);
        assertEquals(row.getEventId(), payload.eventId(), "payload carries the same eventId as the row/header");
        assertEquals(42L, payload.tradeId());
        assertEquals("c-1", payload.clientTradeId());
        assertEquals(7L, payload.userAccountId());
        assertEquals(3L, payload.stockId());
        assertEquals(TradeType.SELL, payload.tradeType());
        assertEquals(4, payload.quantity());
        assertEquals(new BigDecimal("120.5000"), payload.price());
        assertEquals(LocalDateTime.of(2026, 9, 13, 12, 0), payload.occurredAt());
    }

    @Test
    void recordTradeExecuted_fallsBackToNow_whenCreatedAtNotYetGenerated() {
        UserAccount user = new UserAccount(1L, "U", "u@example.com", null);
        Stock stock = new Stock(2L, "BOLT", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        Trade trade = Trade.builder().id(1L).userAccount(user).stock(stock).tradeType(TradeType.BUY).quantity(1)
                .price(BigDecimal.ONE).build();

        OutboxEvent row = outboxService.recordTradeExecuted(trade);

        assertTrue(row.getPayload().contains("\"occurredAt\""));
        assertFalse(row.getPayload().contains("\"occurredAt\":null"));
    }

    @Test
    void everyEventGetsItsOwnId() {
        UserAccount user = new UserAccount(1L, "U", "u@example.com", null);
        Stock stock = new Stock(2L, "BOLT", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        Trade trade = Trade.builder().id(1L).userAccount(user).stock(stock).tradeType(TradeType.BUY).quantity(1)
                .price(BigDecimal.ONE).build();

        assertNotEquals(outboxService.recordTradeExecuted(trade).getEventId(),
                outboxService.recordTradeExecuted(trade).getEventId());
    }
}
