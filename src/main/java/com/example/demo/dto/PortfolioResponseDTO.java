package com.example.demo.dto;

import lombok.Data;
import java.util.List;

@Data
public class PortfolioResponseDTO {
    private List<PortfolioHoldingDTO> holdings;
    private Double totalHoldingValue;
    private Double totalBuyPrice;
    private Double totalPL;
    private Double totalPLPercentage;
}

