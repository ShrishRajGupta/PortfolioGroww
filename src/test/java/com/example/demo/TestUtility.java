package com.example.demo;

import com.example.demo.dto.*;
import com.example.demo.repository.*;
import com.example.demo.entity.*;
import com.example.demo.service.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;

@Component
public class TestUtility {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    public PortfolioResponseDTO getPortfolioForUser(Long userId) {
        return portfolioService.getPortfolio(userId);
    }

    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    public UserAccount getUserById(Long userId) {
        return userAccountRepository.findById(userId).orElse(null);
    }

    public void updateStocksFromCsv(String csvData) {
        MultipartFile file = new MockMultipartFile("stocks.csv", csvData.getBytes());
        stockService.updateStocksFromCsv(file);
    }
}
