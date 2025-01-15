package com.example.demo.service;

import com.example.demo.dto.PortfolioResponseDTO;

public interface PortfolioService {
    PortfolioResponseDTO getPortfolio(Long userId);
}
