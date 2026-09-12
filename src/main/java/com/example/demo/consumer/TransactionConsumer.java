package com.example.demo.consumer;


import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class TransactionConsumer {
    private final TradeService tradeService;

    @KafkaListener(topics = "newTransactions", groupId = "spend-analysis-group",
                   autoStartup = "${app.kafka.consumer-enabled}")
    public void consumeTransaction(TradeRequestDTO transactionRequest) {
        tradeService.recordTrade(transactionRequest);
    }
}
