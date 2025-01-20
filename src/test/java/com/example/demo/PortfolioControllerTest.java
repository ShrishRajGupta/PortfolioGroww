package com.example.demo;

import com.example.demo.controller.PortfolioController;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.dto.*;
import com.example.demo.entity.Stock;
import com.example.demo.service.Impl.PortfolioServiceImpl;
import com.example.demo.service.Impl.StockServiceImpl;
import com.example.demo.service.Impl.TradeServiceImpl;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test
    void testGetPortfolio_Success() {
        PortfolioResponseDTO mockResponse = new PortfolioResponseDTO();
        mockResponse.setHoldings(Collections.emptyList());
        mockResponse.setTotalHoldingValue(1000.0);
        mockResponse.setTotalBuyPrice(900.0);
        mockResponse.setTotalPL(100.0);
        mockResponse.setTotalPLPercentage(11.11);

        when(portfolioService.getPortfolio(1L)).thenReturn(mockResponse);

        ResponseEntity<PortfolioResponseDTO> response = portfolioController.getPortfolio(1L);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(mockResponse, response.getBody());
        verify(portfolioService, times(1)).getPortfolio(1L);
    }

    @Test
    void testGetPortfolio_UserNotFound() {
        when(portfolioService.getPortfolio(999L)).thenThrow(new IllegalArgumentException("User not found"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            portfolioController.getPortfolio(999L);
        });

        assertEquals("User not found", exception.getMessage());
        verify(portfolioService, times(1)).getPortfolio(999L);
    }

    @Test
    void testGetPortfolio_EmptyPortfolio() {
        PortfolioResponseDTO emptyResponse = new PortfolioResponseDTO();
        emptyResponse.setHoldings(Collections.emptyList());
        emptyResponse.setTotalHoldingValue(0.0);
        emptyResponse.setTotalBuyPrice(0.0);
        emptyResponse.setTotalPL(0.0);
        emptyResponse.setTotalPLPercentage(0.0);

        when(portfolioService.getPortfolio(2L)).thenReturn(emptyResponse);

        ResponseEntity<PortfolioResponseDTO> response = portfolioController.getPortfolio(2L);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(emptyResponse, response.getBody());
        verify(portfolioService, times(1)).getPortfolio(2L);
    }

    @Test
    void testRecordTrade() {
        TradeRequestDTO tradeRequest = new TradeRequestDTO(1L, 1L, "BUY", 10);
        TradeResponseDTO tradeResponse = new TradeResponseDTO("SUCCESS", "Trade recorded successfully");

        when(tradeService.recordTrade(tradeRequest)).thenReturn(tradeResponse);

        ResponseEntity<TradeResponseDTO> response = portfolioController.recordTrade(tradeRequest);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("SUCCESS", response.getBody().getStatus());
        assertEquals("Trade recorded successfully", response.getBody().getMessage());

        verify(tradeService, times(1)).recordTrade(tradeRequest);
    }

@Test
void testGetPortfolio() {
    Long userId = 1L;
    PortfolioResponseDTO portfolioResponse = new PortfolioResponseDTO();
    portfolioResponse.setTotalHoldingValue(1000.0);
    portfolioResponse.setTotalBuyPrice(900.0);

    when(portfolioService.getPortfolio(userId)).thenReturn(portfolioResponse);

    ResponseEntity<PortfolioResponseDTO> response = portfolioController.getPortfolio(userId);

    assertNotNull(response);
    assertEquals(200, response.getStatusCodeValue());
    assertEquals(1000.0, response.getBody().getTotalHoldingValue());
    assertEquals(900.0, response.getBody().getTotalBuyPrice());

    verify(portfolioService, times(1)).getPortfolio(userId);
}

@Test
void testGetStockById() {
    Long stockId = 1L;
    Stock stock = new Stock(1L, "Stock1", 100.0, 105.0, 110.0, 95.0, 102.5);

    when(stockService.findStockById(stockId)).thenReturn(Optional.of(stock));

    ResponseEntity<Optional<Stock>> response = portfolioController.getStockById(stockId);

    assertNotNull(response);
    assertEquals(200, response.getStatusCodeValue());
    assertTrue(response.getBody().isPresent());
    assertEquals("Stock1", response.getBody().get().getName());

    verify(stockService, times(1)).findStockById(stockId);
}

@Test
void testUpdateStocks() {
    MockMultipartFile file = new MockMultipartFile("file", "stocks.csv", "text/csv", "Stock1,100,105,110,95,102.5".getBytes());

    doNothing().when(stockService).processCsv(file);

    ResponseEntity<String> response = portfolioController.updateStocks(file);

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertEquals("Stocks updated successfully", response.getBody());

    verify(stockService, times(1)).processCsv(file);
}

@Test
void testSearchStock() {
    String stockName = "Stock";
    List<Stock> stocks = new ArrayList<>();
    stocks.add(new Stock(1L, "Stock1", 100.0, 105.0, 110.0, 95.0, 102.5));

    when(stockService.searchStockByName(stockName)).thenReturn(stocks);

    ResponseEntity<?> response = portfolioController.searchStock(stockName);

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertEquals(1, ((List<?>) Objects.requireNonNull(response.getBody())).size());

    verify(stockService, times(1)).searchStockByName(stockName);
}
}

