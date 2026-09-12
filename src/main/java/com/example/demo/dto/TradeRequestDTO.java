package com.example.demo.dto;

import com.example.demo.entity.enums.TradeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeRequestDTO {

    /** Optional idempotency key (e.g. a UUID). Same key => same trade, never a second booking. */
    @Size(max = 36, message = "must be at most 36 characters")
    private String clientTradeId;

    @NotNull(message = "must not be null")
    private Long userAccountId;

    @NotNull(message = "must not be null")
    private Long stockId;

    @NotNull(message = "must be BUY or SELL")
    private TradeType tradeType;

    @NotNull(message = "must not be null")
    @Positive(message = "must be greater than 0")
    private Integer quantity;

    public TradeRequestDTO(Long userAccountId, Long stockId, TradeType tradeType, Integer quantity) {
        this(null, userAccountId, stockId, tradeType, quantity);
    }
}
