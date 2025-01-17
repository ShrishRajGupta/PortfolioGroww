package com.example.demo.publisher.impl;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.publisher.TransactionPublisher;

import org.springframework.kafka.core.KafkaTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionPublisherImpl implements TransactionPublisher{
    private final KafkaTemplate<String, TradeRequestDTO> kafkaTemplate;

    @Override
    public void publishTransaction(TradeRequestDTO transactionRequest) {
        // Logic to publish transaction
        kafkaTemplate.send("newTransactions", transactionRequest);
    }
}
