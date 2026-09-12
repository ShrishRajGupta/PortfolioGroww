package com.example.demo.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockDetailsDTO {
    private Long stockId;
    private String stockName;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal settlementPrice;
}
