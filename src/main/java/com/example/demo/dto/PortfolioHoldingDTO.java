package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One open position, valued at the stock's current close price. All money has 4 decimals. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioHoldingDTO {
    private Long stockId;
    private String stockName;
    /** Units currently held (BUYs minus SELLs). */
    private Integer netQuantity;
    /** Weighted-average cost per unit of the units still held. */
    private BigDecimal avgCost;
    private BigDecimal marketPrice;
    /** avgCost x netQuantity */
    private BigDecimal costBasis;
    /** marketPrice x netQuantity */
    private BigDecimal marketValue;
    /** marketValue - costBasis */
    private BigDecimal unrealizedPnl;
    /** Profit/loss locked in by SELLs of this stock so far. */
    private BigDecimal realizedPnl;
}
