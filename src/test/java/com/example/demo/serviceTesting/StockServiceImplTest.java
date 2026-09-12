package com.example.demo.serviceTesting;

import com.example.demo.entity.Stock;
import com.example.demo.repository.StockRepository;
import com.example.demo.service.Impl.StockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StockServiceImplTest {

    @Mock
    private StockRepository stockRepository;

    private StockServiceImpl stockService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stockService = new StockServiceImpl(stockRepository, "http://example.invalid/stocks.csv");
    }

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "stocks.csv", "text/csv", body.getBytes());
    }

    private static Stock stock1(String close) {
        return new Stock(1L, "Stock1", new BigDecimal("100"), new BigDecimal(close),
                new BigDecimal("110"), new BigDecimal("95"), new BigDecimal("102.5"));
    }

    @SuppressWarnings("unchecked")
    private List<Stock> capturedBatch() {
        ArgumentCaptor<List<Stock>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockRepository, times(1)).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void processCsv_insertsNewStocksInOneBatch_withExactDecimals() {
        when(stockRepository.findByName(anyString())).thenReturn(List.of());

        int applied = stockService.processCsv(csv("Stock1,100,105,110,95,102.5\nStock2,200,205,210,195,202.5"));

        assertEquals(2, applied);
        List<Stock> batch = capturedBatch();
        assertEquals(2, batch.size());
        assertEquals("Stock1", batch.get(0).getName());
        assertEquals(new BigDecimal("105"), batch.get(0).getClosePrice());
        assertEquals(new BigDecimal("102.5"), batch.get(0).getSettlementPrice(), "no float rounding");
        assertEquals("Stock2", batch.get(1).getName());
        assertNull(batch.get(0).getId(), "new stock must not carry an id");
    }

    @Test
    void processCsv_updatesExistingStockByNameInsteadOfDuplicating() {
        Stock existing = new Stock(7L, "Stock1", new BigDecimal("1"), new BigDecimal("1"),
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"));
        when(stockRepository.findByName("Stock1")).thenReturn(List.of(existing));

        int applied = stockService.processCsv(csv("Stock1,100,105,110,95,102.5"));

        assertEquals(1, applied);
        List<Stock> batch = capturedBatch();
        assertSame(existing, batch.get(0), "existing row is updated, not replaced");
        assertEquals(7L, batch.get(0).getId());
        assertEquals(new BigDecimal("105"), batch.get(0).getClosePrice());
        assertEquals(new BigDecimal("95"), batch.get(0).getLowPrice());
    }

    @Test
    void processCsv_skipsHeaderAndMalformedRows() {
        when(stockRepository.findByName(anyString())).thenReturn(List.of());

        int applied = stockService.processCsv(csv(
                "name,open,close,high,low,settlement\n" +   // header -> non-numeric, skipped
                "Stock1,100,105,110,95,102.5\n" +
                "too,short\n" +                              // < 6 columns, skipped
                "\n" +                                       // blank, ignored
                " Stock2 , 200 ,205,210,195,202.5"));       // trimmed

        assertEquals(2, applied);
        List<Stock> batch = capturedBatch();
        assertEquals(List.of("Stock1", "Stock2"), batch.stream().map(Stock::getName).toList());
        assertEquals(new BigDecimal("200"), batch.get(1).getOpenPrice());
    }

    @Test
    void searchStockByName_delegatesTrimmedQuery() {
        List<Stock> mockStocks = new ArrayList<>();
        mockStocks.add(stock1("105"));
        when(stockRepository.findByNameContainingIgnoreCase("Stock")).thenReturn(mockStocks);

        List<Stock> stocks = stockService.searchStockByName("  Stock ");

        assertEquals(1, stocks.size());
        assertEquals("Stock1", stocks.get(0).getName());
        verify(stockRepository).findByNameContainingIgnoreCase("Stock");
    }

    @Test
    void findStockById_delegates() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock1("105")));

        Optional<Stock> stock = stockService.findStockById(1L);

        assertTrue(stock.isPresent());
        assertEquals("Stock1", stock.get().getName());
        verify(stockRepository).findById(1L);
        verify(stockRepository, never()).saveAll(any());
    }
}
