package com.example.demo.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PortfolioHoldingDTO {
    private String stockName;
    private Long stockId;
    private Integer quantity;
    private BigDecimal buyPrice;
    private BigDecimal currentPrice;
    private BigDecimal gainLoss;
}
