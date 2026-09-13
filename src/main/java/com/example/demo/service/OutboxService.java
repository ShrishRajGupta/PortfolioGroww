package com.example.demo.service;

import com.example.demo.entity.OutboxEvent;
import com.example.demo.entity.Trade;

public interface OutboxService {

    /**
     * Records a TradeExecuted event for a just-booked trade. Must be called inside the transaction
     * that books the trade so the event is committed with it or not at all.
     */
    OutboxEvent recordTradeExecuted(Trade trade);
}
