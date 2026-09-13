package com.example.demo.service.Impl;

import com.example.demo.entity.OutboxEvent;
import com.example.demo.entity.Trade;
import com.example.demo.event.TradeExecutedEvent;
import com.example.demo.repository.OutboxRepository;
import com.example.demo.service.OutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEvent recordTradeExecuted(Trade trade) {
        String eventId = UUID.randomUUID().toString();
        TradeExecutedEvent event = new TradeExecutedEvent(
                eventId,
                trade.getId(),
                trade.getClientTradeId(),
                trade.getUserAccount().getId(),
                trade.getStock().getId(),
                trade.getTradeType(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getCreatedAt() != null ? trade.getCreatedAt() : LocalDateTime.now());

        return outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType(TradeExecutedEvent.AGGREGATE)
                .aggregateId(trade.getId())
                .eventType(TradeExecutedEvent.TYPE)
                .messageKey(String.valueOf(trade.getUserAccount().getId()))
                .payload(toJson(event))
                .attempts(0)
                .build());
    }

    private String toJson(TradeExecutedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize TradeExecuted event for trade " + event.tradeId(), e);
        }
    }
}
