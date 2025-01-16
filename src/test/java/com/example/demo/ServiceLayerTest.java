package com.example.demo;

import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.*;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.PortfolioService;
import com.example.demo.service.StockService;
import com.example.demo.service.TradeService;
import com.example.demo.service.Impl.PortfolioServiceImpl;
import com.example.demo.service.Impl.StockServiceImpl;
import com.example.demo.service.Impl.TradeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceLayerTest {

    @Mock
    @Autowired
    private TradeRepository tradeRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    @Autowired
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private PortfolioService portfolioService = new PortfolioServiceImpl(tradeRepository, stockRepository);

    @InjectMocks
    private StockService stockService = new StockServiceImpl(stockRepository);

    @InjectMocks
    private TradeService tradeService = new TradeServiceImpl(tradeRepository, stockRepository, userAccountRepository);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetPortfolio_Success() {
        UserAccount user = new UserAccount(1L, "User1", "user1@example.com", null);
        Stock stock = new Stock(1L, "Stock1", 100.0, 105.0, 110.0, 95.0, 102.5);

        Trade trade = new Trade();
        trade.setUserAccount(user);
        trade.setStock(stock);
        trade.setTradeType("BUY");
        trade.setQuantity(10);
        trade.setPrice(100.0);

        when(tradeRepository.findByUserAccountId(1L)).thenReturn(List.of(trade));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));

        PortfolioResponseDTO response = portfolioService.getPortfolio(1L);

        assertNotNull(response);
        assertEquals(1, response.getHoldings().size());
        assertEquals(1050.0, response.getTotalHoldingValue());
        assertEquals(1000.0, response.getTotalBuyPrice());
        assertEquals(50.0, response.getTotalPL());
    }

    @Test
    void testFindStockById_Success() {
        Stock stock = new Stock(1L, "Stock1", 100.0, 105.0, 110.0, 95.0, 102.5);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));

        Optional<Stock> foundStock = stockService.findStockById(1L);

        assertTrue(foundStock.isPresent());
        assertEquals("Stock1", foundStock.get().getName());
    }

    @Test
    void testFindStockById_NotFound() {
        when(stockRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Stock> foundStock = stockService.findStockById(999L);

        assertFalse(foundStock.isPresent());
    }

    @Test
    void testRecordTrade_Success() {
        UserAccount user = new UserAccount(1L, "User1", "user1@example.com", null);
        Stock stock = new Stock(1L, "Stock1", 100.0, 105.0, 110.0, 95.0, 102.5);

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeRequestDTO tradeRequest = new TradeRequestDTO(1L, 1L, "BUY", 10);
        TradeResponseDTO response = tradeService.recordTrade(tradeRequest);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Trade recorded successfully.", response.getMessage());
        verify(tradeRepository, times(1)).save(any(Trade.class));
    }

    @Test
    void testRecordTrade_UserNotFound() {
        when(userAccountRepository.findById(999L)).thenReturn(Optional.empty());

        TradeRequestDTO tradeRequest = new TradeRequestDTO(999L, 1L, "BUY", 10);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            tradeService.recordTrade(tradeRequest);
        });

        assertEquals("User not found.", exception.getMessage());
        verify(tradeRepository, never()).save(any(Trade.class));
    }

    @Test
    void testRecordTrade_StockNotFound() {
        UserAccount user = new UserAccount(1L, "User1", "user1@example.com", null);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById(999L)).thenReturn(Optional.empty());

        TradeRequestDTO tradeRequest = new TradeRequestDTO(1L, 999L, "BUY", 10);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            tradeService.recordTrade(tradeRequest);
        });

        assertEquals("Stock not found.", exception.getMessage());
        verify(tradeRepository, never()).save(any(Trade.class));
    }
}