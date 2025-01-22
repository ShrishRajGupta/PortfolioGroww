package com.example.demo.ApiTesting;

import com.example.demo.entity.Stock;
import com.example.demo.service.StockService;
import com.example.demo.controller.PortfolioController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class searchApiTesting {

    @Autowired
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
        String stockName = "Stock";
        List<Stock> stocks = new ArrayList<>();
        stocks.add(new Stock(1L, "Stock1", 100.0, 105.0, 110.0, 95.0, 102.5));

        when(stockService.searchStockByName(stockName)).thenReturn(stocks);

        mockMvc.perform(get("/api/stocks/search")
                        .param("stock", stockName)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Stock1"));

        verify(stockService, times(1)).searchStockByName(stockName);
    }

    @Test
    void testSearchStock_InvalidName_Empty() throws Exception {
        mockMvc.perform(get("/api/stocks/search")
                        .param("stock", "")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Stock name must not be empty."));

        verifyNoInteractions(stockService);
    }

    @Test
    void testSearchStock_InvalidName_Short() throws Exception {
        mockMvc.perform(get("/api/stocks/search")
                        .param("stock", "S")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Stock name must be at least 2 characters long."));

        verifyNoInteractions(stockService);
    }

    @Test
    void testSearchStock_InvalidName_Characters() throws Exception {
        mockMvc.perform(get("/api/stocks/search")
                        .param("stock", "St@ck$")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Stock name contains invalid characters. Only letters, numbers, and spaces are allowed."));

        verifyNoInteractions(stockService);
    }

    @Test
    void testSearchStock_NotFound() throws Exception {
        String stockName = "NonExistentStock";
        when(stockService.searchStockByName(stockName)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/stocks/search")
                        .param("stock", stockName)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(stockService, times(1)).searchStockByName(stockName);
    }
}

