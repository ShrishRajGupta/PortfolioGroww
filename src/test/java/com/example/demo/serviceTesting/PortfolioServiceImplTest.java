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

import java.math.BigDecimal;
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

    private static Stock stock(long id, String name, String close) {
        return new Stock(id, name, new BigDecimal("1"), new BigDecimal(close),
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"));
    }

    private static Trade trade(long id, Stock stock, int qty, String price) {
        Trade t = new Trade();
        t.setId(id);
        t.setStock(stock);
        t.setQuantity(qty);
        t.setPrice(new BigDecimal(price));
        return t;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void getPortfolio_valuesHoldingsAtClosePriceAndComputesTotals() {
        Stock stock1 = stock(1L, "Stock1", "110");
        Stock stock2 = stock(2L, "Stock2", "220");
        when(tradeRepository.findByUserAccountId(1L))
                .thenReturn(List.of(trade(1L, stock1, 10, "100"), trade(2L, stock2, 5, "200")));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock1));
        when(stockRepository.findById(2L)).thenReturn(Optional.of(stock2));

        PortfolioResponseDTO response = portfolioService.getPortfolio(1L);

        assertEquals(2, response.getHoldings().size());
        assertMoney("2200", response.getTotalHoldingValue());
        assertMoney("2000", response.getTotalBuyPrice());
        assertMoney("200", response.getTotalPL());
        assertEquals(new BigDecimal("10.00"), response.getTotalPLPercentage());

        PortfolioHoldingDTO h1 = response.getHoldings().get(0);
        assertEquals("Stock1", h1.getStockName());
        assertEquals(10, h1.getQuantity());
        assertMoney("100", h1.getBuyPrice());
        assertMoney("110", h1.getCurrentPrice());
        assertMoney("100", h1.getGainLoss());

        PortfolioHoldingDTO h2 = response.getHoldings().get(1);
        assertMoney("100", h2.getGainLoss());

        verify(stockRepository, times(2)).findById(anyLong());
    }

    @Test
    void getPortfolio_noTrades_returnsZerosNotNaN() {
        when(tradeRepository.findByUserAccountId(2L)).thenReturn(new ArrayList<>());

        PortfolioResponseDTO response = portfolioService.getPortfolio(2L);

        assertTrue(response.getHoldings().isEmpty());
        assertMoney("0", response.getTotalHoldingValue());
        assertMoney("0", response.getTotalBuyPrice());
        assertMoney("0", response.getTotalPL());
        assertEquals(new BigDecimal("0.00"), response.getTotalPLPercentage());
        verifyNoInteractions(stockRepository);
    }

    @Test
    void getPortfolio_skipsTradesWhoseStockIsMissing() {
        Stock stock = stock(1L, "Stock1", "110");
        when(tradeRepository.findByUserAccountId(1L)).thenReturn(List.of(trade(1L, stock, 10, "100")));
        when(stockRepository.findById(1L)).thenReturn(Optional.empty());

        PortfolioResponseDTO response = portfolioService.getPortfolio(1L);

        assertTrue(response.getHoldings().isEmpty());
        assertMoney("0", response.getTotalBuyPrice());
        assertEquals(new BigDecimal("0.00"), response.getTotalPLPercentage());
        verify(stockRepository).findById(1L);
    }

    @Test
    void getPortfolio_negativePnlAndRounding() {
        Stock stock = stock(1L, "Stock1", "99.995");
        when(tradeRepository.findByUserAccountId(1L)).thenReturn(List.of(trade(1L, stock, 3, "100")));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));

        PortfolioResponseDTO response = portfolioService.getPortfolio(1L);

        assertMoney("-0.015", response.getTotalPL());
        // -0.015 / 300 * 100 = -0.005 -> HALF_UP at 2 dp
        assertEquals(new BigDecimal("-0.01"), response.getTotalPLPercentage());
    }
}
