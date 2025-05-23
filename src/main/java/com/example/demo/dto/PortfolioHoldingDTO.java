package com.example.demo.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class PortfolioHoldingDTO {
    private String stockName;
    private Long stockId;
    private Integer quantity;
    private Double buyPrice;
    private Double currentPrice;
    private Double gainLoss;
}
