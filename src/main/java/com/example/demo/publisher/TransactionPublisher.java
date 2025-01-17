package com.example.demo.publisher;

import com.example.demo.dto.TradeRequestDTO;

public interface TransactionPublisher {

    void publishTransaction(TradeRequestDTO transactionRequest);
}
