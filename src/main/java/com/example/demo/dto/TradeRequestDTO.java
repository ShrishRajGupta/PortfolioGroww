package com.example.demo.dto;
import lombok.Data;

@Data
public class TradeRequestDTO {
    private Long userAccountId;
    private Long stockId;
    private String tradeType;
    private Integer quantity;
}
