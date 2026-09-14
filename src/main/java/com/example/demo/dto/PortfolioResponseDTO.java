package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Portfolio valuation. Holdings are open positions only; realized P&amp;L in the totals also
 * includes positions that have been fully closed. All money has 4 decimals, percentages 2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponseDTO {
    private List<PortfolioHoldingDTO> holdings;
    private BigDecimal totalMarketValue;
    private BigDecimal totalCostBasis;
    private BigDecimal totalUnrealizedPnl;
    private BigDecimal totalRealizedPnl;
    /** totalUnrealizedPnl + totalRealizedPnl */
    private BigDecimal totalPnl;
    /** totalUnrealizedPnl / totalCostBasis x 100; 0.00 when nothing is held. */
    private BigDecimal unrealizedReturnPercentage;
}
