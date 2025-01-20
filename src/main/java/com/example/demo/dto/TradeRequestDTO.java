package com.example.demo.dto;
import lombok.Data;

@Data
public class TradeRequestDTO {
    private Long userAccountId;
    private Long stockId;
    private String tradeType;
    private Integer quantity;


    public TradeRequestDTO(long l, long l1, String buy, int i) {
        this.userAccountId=l;
        this.stockId=l1;
        this.tradeType=buy;
        this.quantity=i;
    }
}
