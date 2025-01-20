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

import java.util.List;
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

    @GetMapping("/stocks/search")
    public ResponseEntity<?> searchStock(@RequestParam("stock") String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Stock name must not be empty.");
        }

        if (name.length() < 2) {
            return ResponseEntity.badRequest().body("Stock name must be at least 2 characters long.");
        }

        if (!name.matches("^[a-zA-Z0-9 ]+$")) {
            return ResponseEntity.badRequest().body("Stock name contains invalid characters. Only letters, numbers, and spaces are allowed.");
        }
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        List<Stock> stocks = stockService.searchStockByName(name);
        if (stocks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stocks);
    }
}
