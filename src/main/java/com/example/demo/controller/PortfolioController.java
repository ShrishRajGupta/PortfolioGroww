package com.example.demo.controller;

import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.service.PortfolioService;
import com.example.demo.service.StockService;
import com.example.demo.service.TradeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    public ResponseEntity<TradeResponseDTO> recordTrade(@Valid @RequestBody TradeRequestDTO tradeRequest) {
        return ResponseEntity.ok(tradeService.recordTrade(tradeRequest));
    }

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<PortfolioResponseDTO> getPortfolio(@PathVariable Long userId) {
        return ResponseEntity.ok(portfolioService.getPortfolio(userId));
    }

    @GetMapping("/stocks/{stockId}")
    public ResponseEntity<Stock> getStockById(@PathVariable Long stockId) {
        Stock stock = stockService.findStockById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock", stockId));
        return ResponseEntity.ok(stock);
    }

    @PostMapping("/stocks/update")
    public ResponseEntity<String> updateStocks(@RequestParam("file") MultipartFile file) {
        int applied = stockService.processCsv(file);
        return ResponseEntity.ok("Stocks updated successfully (" + applied + " rows)");
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

        List<Stock> stocks = stockService.searchStockByName(name);
        if (stocks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stocks);
    }
}
