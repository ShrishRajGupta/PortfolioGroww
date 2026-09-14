package com.example.demo.event;

import com.example.demo.entity.enums.TradeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Published on topic {@code trade-executed} for every booked trade. {@code eventId} is the deduplication key. */
public record TradeExecutedEvent(
        String eventId,
        Long tradeId,
        String clientTradeId,
        Long userAccountId,
        Long stockId,
        TradeType tradeType,
        Integer quantity,
        BigDecimal price,
        LocalDateTime occurredAt) {

    public static final String TYPE = "TradeExecuted";
    public static final String AGGREGATE = "Trade";
}
