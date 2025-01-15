package com.example.demo.controller;


import com.example.demo.dto.*;
import com.example.demo.entity.Stock;
import com.example.demo.repository.StockRepository;
import com.example.demo.service.*;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class PortfolioController {

    @Autowired
    private TradeService tradeService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private StockService stockService;

    @PostMapping("/trade")
    public ResponseEntity<TradeResponseDTO> recordTrade(@RequestBody TradeRequestDTO tradeRequest) {
        TradeResponseDTO response = tradeService.recordTrade(tradeRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<PortfolioResponseDTO> getPortfolio(@PathVariable Long userId) {
        PortfolioResponseDTO response = portfolioService.getPortfolio(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stocks/{stock_id}")
    public ResponseEntity<Optional<Stock>> getStockById(@PathVariable Long stock_id){
        Optional<Stock> response= stockService.findStockById(stock_id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stocks/update")
    public ResponseEntity<String> updateStocks(@RequestParam("file") MultipartFile file) {
        stockService.processCsv(file);
        return ResponseEntity.ok("Stocks updated successfully");
    }
}
