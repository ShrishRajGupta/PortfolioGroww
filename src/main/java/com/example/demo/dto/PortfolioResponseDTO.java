package com.example.demo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PortfolioResponseDTO {
    private List<PortfolioHoldingDTO> holdings;
    private BigDecimal totalHoldingValue;
    private BigDecimal totalBuyPrice;
    private BigDecimal totalPL;
    /** Percent, 2 decimals; 0 when there is no cost basis (never NaN). */
    private BigDecimal totalPLPercentage;
}
