package com.example.demo.repository;

import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NOTE: this class mocks the repository it is named after, so it only exercises the entity
 * builders. Real @DataJpaTest coverage arrives with the Testcontainers work.
 */
class TradeRepositoryTest {

    @Mock
    private TradeRepository tradeRepository;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private static UserAccount user() {
        return UserAccount.builder().id(1L).name("John Doe").email("john.doe@example.com").build();
    }

    private static Stock stock(String name, String open, String close, String high, String low) {
        return Stock.builder()
                .id(1L).name(name)
                .openPrice(new BigDecimal(open)).closePrice(new BigDecimal(close))
                .highPrice(new BigDecimal(high)).lowPrice(new BigDecimal(low))
                .settlementPrice(new BigDecimal(close))
                .build();
    }

    @Test
    void testFindByUserAccountId() {
        UserAccount userAccount = user();
        Stock stock = stock("AAPL", "145", "150", "155", "140");

        Trade trade1 = Trade.builder().id(1L).userAccount(userAccount).stock(stock)
                .tradeType(TradeType.BUY).quantity(10).price(new BigDecimal("150"))
                .createdAt(LocalDateTime.now()).build();
        Trade trade2 = Trade.builder().id(2L).userAccount(userAccount).stock(stock)
                .tradeType(TradeType.SELL).quantity(5).price(new BigDecimal("155"))
                .createdAt(LocalDateTime.now()).build();

        when(tradeRepository.findByUserAccountId(1L)).thenReturn(List.of(trade1, trade2));

        List<Trade> result = tradeRepository.findByUserAccountId(1L);

        assertEquals(2, result.size());
        assertEquals(TradeType.BUY, result.get(0).getTradeType());
        assertEquals(TradeType.SELL, result.get(1).getTradeType());
        assertEquals(10, result.get(0).getQuantity());
        assertEquals(5, result.get(1).getQuantity());
        assertEquals("AAPL", result.get(0).getStock().getName());
        assertEquals(new BigDecimal("145"), result.get(0).getStock().getOpenPrice());
        assertEquals(new BigDecimal("155"), result.get(1).getStock().getHighPrice());
    }

    @Test
    void testSaveTrade() {
        UserAccount userAccount = user();
        Stock stock = stock("GOOG", "2800", "2850", "2900", "2750");

        Trade trade = Trade.builder().id(1L).clientTradeId("c-1").userAccount(userAccount).stock(stock)
                .tradeType(TradeType.BUY).quantity(20).price(new BigDecimal("2825"))
                .createdAt(LocalDateTime.now()).build();

        when(tradeRepository.save(trade)).thenReturn(trade);

        Trade savedTrade = tradeRepository.save(trade);

        assertEquals(TradeType.BUY, savedTrade.getTradeType());
        assertEquals(20, savedTrade.getQuantity());
        assertEquals(new BigDecimal("2825"), savedTrade.getPrice());
        assertEquals("c-1", savedTrade.getClientTradeId());
        assertEquals(userAccount, savedTrade.getUserAccount());
        assertEquals(stock, savedTrade.getStock());
    }
}
