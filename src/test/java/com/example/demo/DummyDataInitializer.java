

// 9. Dummy Data for Testing
package com.example.demo;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.stream.IntStream;

@Component
public class DummyDataInitializer {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @PostConstruct
    public void init() {
        // Add 10 User Accounts
        IntStream.rangeClosed(1, 10).forEach(i -> {
            UserAccount user = new UserAccount();
            user.setName("User" + i);
            user.setEmail("user" + i + "@example.com");
            userAccountRepository.save(user);
        });

        // Add 10 Stocks
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

        // Add 10 Trades for each user
        userAccountRepository.findAll().forEach(user -> {
            stockRepository.findAll().forEach(stock -> {
                Trade trade = new Trade();
                trade.setUserAccount(user);
                trade.setStock(stock);
                trade.setTradeType("BUY");
                trade.setQuantity(10);
                trade.setPrice(stock.getOpenPrice());
                tradeRepository.save(trade);
            });
        });
    }
}
