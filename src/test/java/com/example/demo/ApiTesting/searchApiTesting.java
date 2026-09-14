package com.example.demo.ApiTesting;

import com.example.demo.controller.PortfolioController;
import com.example.demo.entity.Stock;
import com.example.demo.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone MockMvc checks for the search endpoint's input validation.
 * NOTE: the class name does not match Surefire's *Test pattern, so this is not run yet;
 * it is renamed and folded into the web test suite in the test-hardening PR.
 */
class searchApiTesting {

    private MockMvc mockMvc;

    @Mock
    private StockService stockService;

    @InjectMocks
    private PortfolioController portfolioController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(portfolioController).build();
    }

    @Test
    void testSearchStock_Success() throws Exception {
        List<Stock> stocks = new ArrayList<>();
        stocks.add(new Stock(1L, "Stock1", new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("110"), new BigDecimal("95"), new BigDecimal("102.5")));
        when(stockService.searchStockByName("Stock")).thenReturn(stocks);

        mockMvc.perform(get("/api/stocks/search").param("stock", "Stock").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Stock1"));
    }

    @Test
    void testSearchStock_InvalidName_Empty() throws Exception {
        mockMvc.perform(get("/api/stocks/search").param("stock", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Stock name must not be empty."));
        verifyNoInteractions(stockService);
    }

    @Test
    void testSearchStock_InvalidName_Short() throws Exception {
        mockMvc.perform(get("/api/stocks/search").param("stock", "S"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Stock name must be at least 2 characters long."));
        verifyNoInteractions(stockService);
    }

    @Test
    void testSearchStock_InvalidName_Characters() throws Exception {
        mockMvc.perform(get("/api/stocks/search").param("stock", "St@ck$"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Stock name contains invalid characters. Only letters, numbers, and spaces are allowed."));
        verifyNoInteractions(stockService);
    }

    @Test
    void testSearchStock_NotFound() throws Exception {
        when(stockService.searchStockByName("NonExistentStock")).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/stocks/search").param("stock", "NonExistentStock"))
                .andExpect(status().isNotFound());
    }
}
