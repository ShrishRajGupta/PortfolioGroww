package com.example.demo;

import com.example.demo.controller.PopulateDatabaseController;
import com.example.demo.entity.*;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    void testPopulateUsers() {
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.populateUsers();

        assertEquals("10 users added successfully.", response.getBody());
        verify(userAccountRepository, times(10)).save(any(UserAccount.class));
    }

    @Test
    void testPopulateStocks() {
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.populateStocks();

        assertEquals("10 stocks added successfully.", response.getBody());
        verify(stockRepository, times(10)).save(any(Stock.class));
    }

    @Test
    void testPopulateTrades() {
        List<UserAccount> users = new ArrayList<>();
        List<Stock> stocks = new ArrayList<>();

        for (int i = 1; i <= 2; i++) {
            users.add(new UserAccount((long) i, "User" + i, "user" + i + "@example.com", null));
        }

        for (int i = 1; i <= 5; i++) {
            stocks.add(new Stock((long) i, "Stock" + i, 100.0 + i, 105.0 + i, 110.0 + i, 95.0 + i, 102.5 + i));
        }

        when(userAccountRepository.findAll()).thenReturn(users);
        when(stockRepository.findAll()).thenReturn(stocks);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.populateTrades();

        assertEquals("Trades with random data added for all users and stocks.", response.getBody());
        verify(tradeRepository, times(users.size() * stocks.size())).save(any(Trade.class));
    }

    @Test
    void testRandomizeStockPrices() {
        List<Stock> stocks = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            stocks.add(new Stock((long) i, "Stock" + i, 100.0, 105.0, 110.0, 95.0, 102.5));
        }

        when(stockRepository.findAll()).thenReturn(stocks);
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<String> response = populateDatabaseController.randomizeStockPrices();

        assertEquals("Stock prices randomized successfully.", response.getBody());
        verify(stockRepository, times(stocks.size())).save(any(Stock.class));

        stocks.forEach(stock -> {
            assertTrue(stock.getOpenPrice() >= 50.0 && stock.getOpenPrice() <= 150.0);
            assertTrue(stock.getClosePrice() >= 50.0 && stock.getClosePrice() <= 150.0);
        });
    }
}
