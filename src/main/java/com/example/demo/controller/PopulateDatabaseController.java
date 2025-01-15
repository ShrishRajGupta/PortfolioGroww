package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/populate")
public class PopulateDatabaseController {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @GetMapping("/users")
    public ResponseEntity<String> populateUsers() {
        IntStream.rangeClosed(1, 10).forEach(i -> {
            UserAccount user = new UserAccount();
            user.setName("User" + i);
            user.setEmail("user" + i + "@example.com");
            userAccountRepository.save(user);
        });
        return ResponseEntity.ok("10 users added successfully.");
    }

    @GetMapping("/stocks")
    public ResponseEntity<String> populateStocks() {
        IntStream.rangeClosed(1, 10).forEach(i -> {
            Stock stock = new Stock();
            stock.setName("Stock" + i);
            stock.setOpenPrice(100.0 + i);
            stock.setClosePrice(105.0 + i);
            stock.setHighPrice(110.0 + i);
            stock.setLowPrice(95.0 + i);
            stock.setSettlementPrice(102.5 + i);
            stockRepository.save(stock);
        });
        return ResponseEntity.ok("10 stocks added successfully.");
    }

    @GetMapping("/trades")
    public ResponseEntity<String> populateTrades() {
        userAccountRepository.findAll().forEach(user -> {
            stockRepository.findAll().forEach(stock -> {
                Trade trade = new Trade();
                trade.setUserAccount(user);
                trade.setStock(stock);
                trade.setTradeType(ThreadLocalRandom.current().nextBoolean() ? "BUY" : "SELL");
                trade.setQuantity(ThreadLocalRandom.current().nextInt(1, 101));
                trade.setPrice(ThreadLocalRandom.current().nextDouble(50.0, 150.0));
                tradeRepository.save(trade);
            });
        });
        return ResponseEntity.ok("Trades with random data added for all users and stocks.");
    }

    @PutMapping("/stocks/update-prices")
    public ResponseEntity<String> randomizeStockPrices() {
        stockRepository.findAll().forEach(stock -> {
            stock.setOpenPrice(ThreadLocalRandom.current().nextDouble(50.0, 150.0));
            stock.setClosePrice(ThreadLocalRandom.current().nextDouble(50.0, 150.0));
            stock.setHighPrice(ThreadLocalRandom.current().nextDouble(stock.getClosePrice(), 200.0));
            stock.setLowPrice(ThreadLocalRandom.current().nextDouble(1.0, stock.getClosePrice()));
            stock.setSettlementPrice(ThreadLocalRandom.current().nextDouble(50.0, 150.0));
            stockRepository.save(stock);
        });
        return ResponseEntity.ok("Stock prices randomized successfully.");
    }
}

