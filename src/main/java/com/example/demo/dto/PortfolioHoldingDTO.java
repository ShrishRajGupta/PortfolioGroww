package com.example.demo.dto;
import lombok.Data;

@Data
public class PortfolioHoldingDTO {
    private String stockName;
    private Long stockId;
    private Integer quantity;
    private Double buyPrice;
    private Double currentPrice;
    private Double gainLoss;
}
