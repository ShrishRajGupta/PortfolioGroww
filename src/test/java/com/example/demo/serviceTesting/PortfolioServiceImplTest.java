package com.example.demo.serviceTesting;


import com.example.demo.dto.PortfolioHoldingDTO;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.service.Impl.PortfolioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioServiceImplTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private PortfolioServiceImpl portfolioService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetPortfolio_Success() {
        Long userId = 1L;

        // Mock trades
        Trade trade1 = new Trade();
        trade1.setId(1L);
        trade1.setQuantity(10);
        trade1.setPrice(100.0);
        Stock stock1 = new Stock(1L, "Stock1", 100.0, 110.0, 120.0, 90.0, 105.0);
        trade1.setStock(stock1);

        Trade trade2 = new Trade();
        trade2.setId(2L);
        trade2.setQuantity(5);
        trade2.setPrice(200.0);
        Stock stock2 = new Stock(2L, "Stock2", 200.0, 220.0, 240.0, 180.0, 215.0);
        trade2.setStock(stock2);

        List<Trade> trades = List.of(trade1, trade2);
        when(tradeRepository.findByUserAccountId(userId)).thenReturn(trades);

        // Mock stocks
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock1));
        when(stockRepository.findById(2L)).thenReturn(Optional.of(stock2));

        // Execute method
        PortfolioResponseDTO response = portfolioService.getPortfolio(userId);

        // Validate response
        assertNotNull(response);
        assertEquals(2, response.getHoldings().size());
        assertEquals(2200.0, response.getTotalHoldingValue());
        assertEquals(2000.0, response.getTotalBuyPrice());
        assertEquals(200.0, response.getTotalPL());
        assertEquals(10.0, response.getTotalPLPercentage(), 0.01);

        // Validate holdings
        PortfolioHoldingDTO holding1 = response.getHoldings().get(0);
        assertEquals("Stock1", holding1.getStockName());
        assertEquals(10, holding1.getQuantity());
        assertEquals(1100.0, holding1.getCurrentPrice() * holding1.getQuantity());

        PortfolioHoldingDTO holding2 = response.getHoldings().get(1);
        assertEquals("Stock2", holding2.getStockName());
        assertEquals(5, holding2.getQuantity());
        assertEquals(1100.0, holding2.getCurrentPrice() * holding2.getQuantity());

        // Verify interactions
        verify(tradeRepository, times(1)).findByUserAccountId(userId);
        verify(stockRepository, times(2)).findById(anyLong());
    }

    @Test
    void testGetPortfolio_NoTrades() {
        Long userId = 2L;

        // Mock trades
        when(tradeRepository.findByUserAccountId(userId)).thenReturn(new ArrayList<>());

        // Execute method
        PortfolioResponseDTO response = portfolioService.getPortfolio(userId);

        // Validate response
        assertNotNull(response);
        assertEquals(0, response.getHoldings().size());
        assertEquals(0.0, response.getTotalHoldingValue());
        assertEquals(0.0, response.getTotalBuyPrice());
        assertEquals(0.0, response.getTotalPL());
        assertEquals(Double.NaN,response.getTotalPLPercentage());

        // Verify interactions
        verify(tradeRepository, times(1)).findByUserAccountId(userId);
        verifyNoInteractions(stockRepository);
    }

    @Test
    void testGetPortfolio_StockNotFound() {
        Long userId = 1L;

        // Mock trades
        Trade trade = new Trade();
        trade.setId(1L);
        trade.setQuantity(10);
        trade.setPrice(100.0);
        Stock stock = new Stock(1L, "Stock1", 100.0, 110.0, 120.0, 90.0, 105.0);
        trade.setStock(stock);

        when(tradeRepository.findByUserAccountId(userId)).thenReturn(List.of(trade));
        when(stockRepository.findById(1L)).thenReturn(Optional.empty());

        // Execute method
        PortfolioResponseDTO response = portfolioService.getPortfolio(userId);

        // Validate response
        assertNotNull(response);
        assertEquals(0, response.getHoldings().size());
        assertEquals(0.0, response.getTotalHoldingValue());
        assertEquals(0.0, response.getTotalBuyPrice());
        assertEquals(0.0, response.getTotalPL());
        assertEquals(Double.NaN,response.getTotalPLPercentage());


        // Verify interactions
        verify(tradeRepository, times(1)).findByUserAccountId(userId);
        verify(stockRepository, times(1)).findById(1L);
    }
}
