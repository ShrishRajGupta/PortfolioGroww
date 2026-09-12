package com.example.demo;

import com.example.demo.controller.PortfolioController;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.service.PortfolioService;
import com.example.demo.service.StockService;
import com.example.demo.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Direct-invocation unit tests. HTTP-level behaviour (validation, problem details) is in web/PortfolioControllerWebTest. */
class PortfolioControllerTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private TradeService tradeService;

    @Mock
    private StockService stockService;

    @InjectMocks
    private PortfolioController portfolioController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static Stock stock1() {
        return new Stock(1L, "Stock1", new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("110"), new BigDecimal("95"), new BigDecimal("102.5"));
    }

    @Test
    void getPortfolio_returnsServiceResult() {
        PortfolioResponseDTO mockResponse = new PortfolioResponseDTO();
        mockResponse.setHoldings(Collections.emptyList());
        mockResponse.setTotalHoldingValue(new BigDecimal("1000"));
        mockResponse.setTotalBuyPrice(new BigDecimal("900"));
        mockResponse.setTotalPL(new BigDecimal("100"));
        mockResponse.setTotalPLPercentage(new BigDecimal("11.11"));
        when(portfolioService.getPortfolio(1L)).thenReturn(mockResponse);

        ResponseEntity<PortfolioResponseDTO> response = portfolioController.getPortfolio(1L);

        assertEquals(200, response.getStatusCode().value());
        assertSame(mockResponse, response.getBody());
        verify(portfolioService).getPortfolio(1L);
    }

    @Test
    void getPortfolio_propagatesServiceExceptions() {
        when(portfolioService.getPortfolio(999L)).thenThrow(new IllegalArgumentException("User not found"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> portfolioController.getPortfolio(999L));

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void recordTrade_returnsServiceResult() {
        TradeRequestDTO tradeRequest = new TradeRequestDTO(1L, 1L, TradeType.BUY, 10);
        TradeResponseDTO tradeResponse = new TradeResponseDTO(5L, "SUCCESS", "Trade recorded successfully");
        when(tradeService.recordTrade(tradeRequest)).thenReturn(tradeResponse);

        ResponseEntity<TradeResponseDTO> response = portfolioController.recordTrade(tradeRequest);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(5L, response.getBody().getTradeId());
        assertEquals("SUCCESS", response.getBody().getStatus());
        verify(tradeService).recordTrade(tradeRequest);
    }

    @Test
    void getStockById_present_returnsStock() {
        when(stockService.findStockById(1L)).thenReturn(Optional.of(stock1()));

        ResponseEntity<Stock> response = portfolioController.getStockById(1L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Stock1", response.getBody().getName());
        assertEquals(new BigDecimal("105"), response.getBody().getClosePrice());
    }

    @Test
    void getStockById_absent_throwsNotFound() {
        when(stockService.findStockById(404L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> portfolioController.getStockById(404L));

        assertEquals("Stock 404 not found", ex.getMessage());
    }

    @Test
    void updateStocks_reportsAppliedRows() {
        MockMultipartFile file = new MockMultipartFile("file", "stocks.csv", "text/csv",
                "Stock1,100,105,110,95,102.5".getBytes());
        when(stockService.processCsv(file)).thenReturn(1);

        ResponseEntity<String> response = portfolioController.updateStocks(file);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Stocks updated successfully (1 rows)", response.getBody());
    }

    @Test
    void searchStock_returnsMatches() {
        when(stockService.searchStockByName("Stock")).thenReturn(List.of(stock1()));

        ResponseEntity<?> response = portfolioController.searchStock("Stock");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, ((List<?>) Objects.requireNonNull(response.getBody())).size());
    }

    @Test
    void searchStock_rejectsBadInputBeforeHittingService() {
        assertEquals(400, portfolioController.searchStock("").getStatusCode().value());
        assertEquals(400, portfolioController.searchStock("S").getStatusCode().value());
        assertEquals(400, portfolioController.searchStock("St@ck$").getStatusCode().value());
        verifyNoInteractions(stockService);
    }
}
