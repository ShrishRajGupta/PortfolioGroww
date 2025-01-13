package com.example.demo.dto;

import lombok.Data;

@Data
public class StockDetailsDTO {
    private Long stockId;
    private String stockName;
    private Double openPrice;
    private Double closePrice;
    private Double highPrice;
    private Double lowPrice;
    private Double settlementPrice;
}
