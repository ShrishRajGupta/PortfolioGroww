package com.example.demo.controller;

import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seed/randomize helpers for local development and demos.
 * Dev-profile only: these mutate the database and must never ship to production.
 * Seeding is idempotent — rows that already exist (by email / stock name) are skipped, so the
 * unique constraints introduced in V2 don't turn a second call into a 409.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/populate")
public class PopulateDatabaseController {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @PostMapping("/users")
    public ResponseEntity<String> populateUsers() {
        int added = 0;
        for (int i = 1; i <= 10; i++) {
            String email = "user" + i + "@example.com";
            if (userAccountRepository.findByEmail(email).isPresent()) {
                continue;
            }
            UserAccount user = new UserAccount();
            user.setName("User" + i);
            user.setEmail(email);
            userAccountRepository.save(user);
            added++;
        }
        return ResponseEntity.ok(seedMessage(added, 10, "users"));
    }

    @PostMapping("/stocks")
    public ResponseEntity<String> populateStocks() {
        int added = 0;
        for (int i = 1; i <= 10; i++) {
            String name = "Stock" + i;
            if (!stockRepository.findByName(name).isEmpty()) {
                continue;
            }
            Stock stock = new Stock();
            stock.setName(name);
            stock.setOpenPrice(money(100.0 + i));
            stock.setClosePrice(money(105.0 + i));
            stock.setHighPrice(money(110.0 + i));
            stock.setLowPrice(money(95.0 + i));
            stock.setSettlementPrice(money(102.5 + i));
            stockRepository.save(stock);
            added++;
        }
        return ResponseEntity.ok(seedMessage(added, 10, "stocks"));
    }

    /**
     * Writes a consistent ledger: every (user, stock) gets a BUY, and about half of them a later
     * SELL of at most the bought quantity — never a SELL without a position, so the seeded data
     * obeys the same rule the API enforces.
     */
    @PostMapping("/trades")
    public ResponseEntity<String> populateTrades() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        userAccountRepository.findAll().forEach(user ->
                stockRepository.findAll().forEach(stock -> {
                    int bought = rnd.nextInt(10, 101);
                    tradeRepository.save(trade(user, stock, TradeType.BUY, bought, rnd));
                    if (rnd.nextBoolean()) {
                        tradeRepository.save(trade(user, stock, TradeType.SELL, rnd.nextInt(1, bought + 1), rnd));
                    }
                }));
        return ResponseEntity.ok("Trades with random data added for all users and stocks.");
    }

    @PutMapping("/stocks/update-prices")
    public ResponseEntity<String> randomizeStockPrices() {
        stockRepository.findAll().forEach(stock -> {
            double close = ThreadLocalRandom.current().nextDouble(50.0, 150.0);
            stock.setOpenPrice(money(ThreadLocalRandom.current().nextDouble(50.0, 150.0)));
            stock.setClosePrice(money(close));
            stock.setHighPrice(money(ThreadLocalRandom.current().nextDouble(close, 200.0)));
            stock.setLowPrice(money(ThreadLocalRandom.current().nextDouble(1.0, close)));
            stock.setSettlementPrice(money(ThreadLocalRandom.current().nextDouble(50.0, 150.0)));
            stockRepository.save(stock);
        });
        return ResponseEntity.ok("Stock prices randomized successfully.");
    }

    private static Trade trade(UserAccount user, Stock stock, TradeType type, int quantity, ThreadLocalRandom rnd) {
        Trade trade = new Trade();
        trade.setUserAccount(user);
        trade.setStock(stock);
        trade.setTradeType(type);
        trade.setQuantity(quantity);
        trade.setPrice(money(rnd.nextDouble(50.0, 150.0)));
        return trade;
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static String seedMessage(int added, int requested, String what) {
        return added == requested
                ? requested + " " + what + " added successfully."
                : added + " " + what + " added successfully (" + (requested - added) + " already existed).";
    }
}
