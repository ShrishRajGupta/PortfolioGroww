package com.example.demo.service;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;

public interface TradeService {
    TradeResponseDTO recordTrade(TradeRequestDTO tradeRequest);
}
