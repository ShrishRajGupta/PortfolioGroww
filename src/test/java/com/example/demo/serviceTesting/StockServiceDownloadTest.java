package com.example.demo.serviceTesting;

import com.example.demo.entity.Stock;
import com.example.demo.repository.StockRepository;
import com.example.demo.service.Impl.StockServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** The scheduled download path against a real local HTTP server — success, HTTP error, and unreachable host. */
class StockServiceDownloadTest {

    @Mock
    private StockRepository stockRepository;

    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = "";

    @BeforeEach
    void startServer() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(stockRepository.findByName(anyString())).thenReturn(List.of());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stocks.csv", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private StockServiceImpl serviceFor(String url) {
        return new StockServiceImpl(stockRepository, url);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/stocks.csv";
    }

    @SuppressWarnings("unchecked")
    private List<Stock> savedBatch() {
        ArgumentCaptor<List<Stock>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void download_upsertsEveryRow_skippingTheHeader() {
        status = 200;
        body = "name,open,close,high,low,settle\nStock1,100,105,110,95,102.5\nStock2,200,205,210,195,202.5\n";

        serviceFor(url()).downloadAndProcessStockFile();

        List<Stock> batch = savedBatch();
        assertEquals(List.of("Stock1", "Stock2"), batch.stream().map(Stock::getName).toList());
        assertEquals(new BigDecimal("105"), batch.get(0).getClosePrice());
        assertEquals(new BigDecimal("202.5"), batch.get(1).getSettlementPrice());
    }

    @Test
    void download_nonSuccessStatus_writesNothing() {
        status = 503;
        body = "maintenance";

        serviceFor(url()).downloadAndProcessStockFile();

        verify(stockRepository, never()).saveAll(any());
    }

    @Test
    void download_unreachableHost_isLoggedNotThrown() {
        int deadPort = server.getAddress().getPort();
        server.stop(0);

        assertDoesNotThrow(() -> serviceFor("http://127.0.0.1:" + deadPort + "/stocks.csv").downloadAndProcessStockFile());
        verify(stockRepository, never()).saveAll(any());
    }

    @Test
    void download_emptyBody_writesNothing() {
        status = 200;
        body = "";

        serviceFor(url()).downloadAndProcessStockFile();

        assertTrue(savedBatch().isEmpty());
    }
}
