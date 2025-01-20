package com.example.demo.repository;

import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeRepositoryTest {

    @Mock
    private TradeRepository tradeRepository;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFindByUserAccountId() {
        // Arrange
        Long userAccountId = 1L;

        UserAccount userAccount = UserAccount.builder()
                .id(userAccountId)
                .name("John Doe")
                .email("john.doe@example.com")
                .build();

        Stock stock = Stock.builder()
                .id(1L)
                .name("AAPL")
                .openPrice(145.0)
                .closePrice(150.0)
                .highPrice(155.0)
                .lowPrice(140.0)
                .settlementPrice(150.0)
                .build();

        Trade trade1 = Trade.builder()
                .id(1L)
                .userAccount(userAccount)
                .stock(stock)
                .tradeType("BUY")
                .quantity(10)
                .price(150.0)
                .createdAt(LocalDateTime.now())
                .build();

        Trade trade2 = Trade.builder()
                .id(2L)
                .userAccount(userAccount)
                .stock(stock)
                .tradeType("SELL")
                .quantity(5)
                .price(155.0)
                .createdAt(LocalDateTime.now())
                .build();

        List<Trade> mockTrades = List.of(trade1, trade2);

        when(tradeRepository.findByUserAccountId(userAccountId)).thenReturn(mockTrades);

        // Act
        List<Trade> result = tradeRepository.findByUserAccountId(userAccountId);

        // Assert
        assertEquals(2, result.size());
        assertEquals("BUY", result.get(0).getTradeType());
        assertEquals("SELL", result.get(1).getTradeType());
        assertEquals(10, result.get(0).getQuantity());
        assertEquals(5, result.get(1).getQuantity());
        assertEquals("AAPL", result.get(0).getStock().getName());
        assertEquals(145.0, result.get(0).getStock().getOpenPrice());
        assertEquals(155.0, result.get(1).getStock().getHighPrice());
    }

    @Test
    void testSaveTrade() {
        // Arrange
        UserAccount userAccount = UserAccount.builder()
                .id(1L)
                .name("John Doe")
                .email("john.doe@example.com")
                .build();

        Stock stock = Stock.builder()
                .id(1L)
                .name("GOOG")
                .openPrice(2800.0)
                .closePrice(2850.0)
                .highPrice(2900.0)
                .lowPrice(2750.0)
                .settlementPrice(2850.0)
                .build();

        Trade trade = Trade.builder()
                .id(1L)
                .userAccount(userAccount)
                .stock(stock)
                .tradeType("BUY")
                .quantity(20)
                .price(2825.0)
                .createdAt(LocalDateTime.now())
                .build();

        when(tradeRepository.save(trade)).thenReturn(trade);

        // Act
        Trade savedTrade = tradeRepository.save(trade);

        // Assert
        assertEquals("BUY", savedTrade.getTradeType());
        assertEquals(20, savedTrade.getQuantity());
        assertEquals(2825.0, savedTrade.getPrice());
        assertEquals(userAccount, savedTrade.getUserAccount());
        assertEquals(stock, savedTrade.getStock());
        assertEquals("GOOG", savedTrade.getStock().getName());
    }
}
