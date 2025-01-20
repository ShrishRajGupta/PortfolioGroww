package com.example.demo.serviceTesting;

import com.example.demo.entity.Stock;
import com.example.demo.repository.StockRepository;
import com.example.demo.service.Impl.StockServiceImpl;
import com.example.demo.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockServiceImplTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockServiceImpl stockService;

    @Value("${spring.datasource.stock-sheet-url}")
    private String csvUrl;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void testProcessCsv() {
        String csvData = "Stock1,100,105,110,95,102.5\nStock2,200,205,210,195,202.5";
        MockMultipartFile file = new MockMultipartFile("file", "stocks.csv", "text/csv", csvData.getBytes());

        stockService.processCsv(file);

        verify(stockRepository, times(2)).save(any(Stock.class));
    }

    @Test
    void testSearchStockByName() {
        List<Stock> mockStocks = new ArrayList<>();
        mockStocks.add(new Stock(1L, "Stock1", 100.0, 105.0, 110.0, 95.0, 102.5));
        when(stockRepository.findByNameContainingIgnoreCase("Stock"))
                .thenReturn(mockStocks);

        List<Stock> stocks = stockService.searchStockByName("Stock");

        assertNotNull(stocks);
        assertEquals(1, stocks.size());
        assertEquals("Stock1", stocks.get(0).getName());
        verify(stockRepository, times(1)).findByNameContainingIgnoreCase("Stock");
    }

    @Test
    void testFindStockById() {
        Stock mockStock = new Stock(1L, "Stock1", 100.0, 105.0, 110.0, 95.0, 102.5);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(mockStock));

        Optional<Stock> stock = stockService.findStockById(1L);

        assertTrue(stock.isPresent());
        assertEquals("Stock1", stock.get().getName());
        verify(stockRepository, times(1)).findById(1L);
    }

    @Test
    void testUpdateStocksFromCsv() {
        String csvData = "Stock1,100,105,110,95,102.5\nStock2,200,205,210,195,202.5";
        MockMultipartFile file = new MockMultipartFile("file", "stocks.csv", "text/csv", csvData.getBytes());

        stockService.updateStocksFromCsv(file);

        verify(stockRepository, times(2)).save(any(Stock.class));
    }
}
