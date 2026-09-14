package com.example.demo;

import com.example.demo.controller.PopulateDatabaseController;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PopulateDatabaseControllerTests {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private PopulateDatabaseController populateDatabaseController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static Stock stock(long i) {
        return new Stock(i, "Stock" + i, new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("110"), new BigDecimal("95"), new BigDecimal("102.5"));
    }

    @Test
    void populateUsers_addsAllWhenNoneExist() {
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.populateUsers();

        assertEquals("10 users added successfully.", response.getBody());
        verify(userAccountRepository, times(10)).save(any(UserAccount.class));
    }

    @Test
    void populateUsers_isIdempotent_skipsExistingEmails() {
        when(userAccountRepository.findByEmail("user1@example.com"))
                .thenReturn(Optional.of(new UserAccount(1L, "User1", "user1@example.com", null)));
        when(userAccountRepository.findByEmail("user2@example.com"))
                .thenReturn(Optional.of(new UserAccount(2L, "User2", "user2@example.com", null)));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.populateUsers();

        assertEquals("8 users added successfully (2 already existed).", response.getBody());
        verify(userAccountRepository, times(8)).save(any(UserAccount.class));
    }

    @Test
    void populateStocks_addsAllWithDecimalPrices() {
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.populateStocks();

        assertEquals("10 stocks added successfully.", response.getBody());
        verify(stockRepository, times(10)).save(argThat(s ->
                s.getClosePrice() != null && s.getClosePrice().scale() == 4));
    }

    @Test
    void populateStocks_isIdempotent_skipsExistingNames() {
        when(stockRepository.findByName("Stock3")).thenReturn(List.of(stock(3)));
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.populateStocks();

        assertEquals("9 stocks added successfully (1 already existed).", response.getBody());
        verify(stockRepository, times(9)).save(any(Stock.class));
    }

    @Test
    void populateTrades_writesAConsistentLedger_buyBeforeAnySell() {
        List<UserAccount> users = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            users.add(new UserAccount((long) i, "User" + i, "user" + i + "@example.com", null));
        }
        List<Stock> stocks = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            stocks.add(stock(i));
        }
        when(userAccountRepository.findAll()).thenReturn(users);
        when(stockRepository.findAll()).thenReturn(stocks);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.populateTrades();

        assertEquals("Trades with random data added for all users and stocks.", response.getBody());
        ArgumentCaptor<Trade> saved = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository, atLeast(users.size() * stocks.size())).save(saved.capture());
        verify(tradeRepository, atMost(2 * users.size() * stocks.size())).save(any(Trade.class));

        // per (user, stock): exactly one BUY, and any SELL is <= that BUY and comes after it
        Map<String, Integer> bought = new HashMap<>();
        for (Trade t : saved.getAllValues()) {
            assertEquals(4, t.getPrice().scale());
            assertTrue(t.getQuantity() > 0);
            String key = t.getUserAccount().getId() + ":" + t.getStock().getId();
            if (t.getTradeType() == TradeType.BUY) {
                assertNull(bought.put(key, t.getQuantity()), "one BUY per pair: " + key);
            } else {
                Integer held = bought.get(key);
                assertNotNull(held, "SELL without a prior BUY: " + key);
                assertTrue(t.getQuantity() <= held, "SELL exceeds BUY: " + key);
            }
        }
        assertEquals(users.size() * stocks.size(), bought.size());
    }

    @Test
    void randomizeStockPrices_keepsHighAboveCloseAboveLow() {
        List<Stock> stocks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            stocks.add(stock(i));
        }
        when(stockRepository.findAll()).thenReturn(stocks);
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.randomizeStockPrices();

        assertEquals("Stock prices randomized successfully.", response.getBody());
        verify(stockRepository, times(stocks.size())).save(any(Stock.class));
        stocks.forEach(s -> {
            assertTrue(s.getClosePrice().compareTo(new BigDecimal("50")) >= 0
                    && s.getClosePrice().compareTo(new BigDecimal("150")) <= 0);
            assertTrue(s.getHighPrice().compareTo(s.getClosePrice()) >= 0);
            assertTrue(s.getLowPrice().compareTo(s.getClosePrice()) <= 0);
        });
    }
}
