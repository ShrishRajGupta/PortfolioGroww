package com.example.demo.service.Impl;

import com.example.demo.entity.Stock;
import com.example.demo.repository.StockRepository;
import com.example.demo.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class StockServiceImpl implements StockService {

    /** name, open, close, high, low, settlement */
    private static final int EXPECTED_COLUMNS = 6;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private final StockRepository stockRepository;
    private final String csvUrl;
    private final HttpClient httpClient;

    public StockServiceImpl(StockRepository stockRepository,
                            @Value("${app.stock-sheet-url}") String csvUrl) {
        this.stockRepository = stockRepository;
        this.csvUrl = csvUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void downloadAndProcessStockFile() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(csvUrl))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                log.error("Stock sheet download failed: HTTP {} from {}", response.statusCode(), csvUrl);
                return;
            }
            try (Reader reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8)) {
                int applied = upsertFromCsv(reader);
                log.info("Stock sheet refresh applied {} rows", applied);
            }
        } catch (IOException e) {
            log.error("Stock sheet download/parse failed for {}", csvUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Stock sheet download interrupted");
        }
    }

    @Override
    public int processCsv(MultipartFile file) {
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return upsertFromCsv(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to process CSV", e);
        }
    }

    @Override
    public List<Stock> searchStockByName(String name) {
        return stockRepository.findByNameContainingIgnoreCase(name.trim());
    }

    @Override
    public Optional<Stock> findStockById(Long id) {
        return stockRepository.findById(id);
    }

    /**
     * Single parse path for uploads and the scheduled download.
     * Rows are matched to existing stocks by name (update) or created (insert), then
     * written in one batch. Header rows and malformed rows are skipped, not fatal.
     */
    private int upsertFromCsv(Reader reader) throws IOException {
        List<Stock> batch = new ArrayList<>();
        try (CSVParser parser = CSV_FORMAT.parse(reader)) {
            for (CSVRecord record : parser) {
                if (record.size() < EXPECTED_COLUMNS) {
                    log.warn("Skipping CSV row {}: expected {} columns, got {}",
                            record.getRecordNumber(), EXPECTED_COLUMNS, record.size());
                    continue;
                }
                String name = record.get(0);
                if (name.isEmpty()) {
                    continue;
                }
                double open, close, high, low, settlement;
                try {
                    open = Double.parseDouble(record.get(1));
                    close = Double.parseDouble(record.get(2));
                    high = Double.parseDouble(record.get(3));
                    low = Double.parseDouble(record.get(4));
                    settlement = Double.parseDouble(record.get(5));
                } catch (NumberFormatException e) {
                    // Most likely a header line.
                    log.debug("Skipping non-numeric CSV row {} ({})", record.getRecordNumber(), name);
                    continue;
                }

                Stock stock = findExistingByName(name).orElseGet(Stock::new);
                stock.setName(name);
                stock.setOpenPrice(open);
                stock.setClosePrice(close);
                stock.setHighPrice(high);
                stock.setLowPrice(low);
                stock.setSettlementPrice(settlement);
                batch.add(stock);
            }
        }
        stockRepository.saveAll(batch);
        return batch.size();
    }

    private Optional<Stock> findExistingByName(String name) {
        List<Stock> matches = stockRepository.findByName(name);
        if (matches.size() > 1) {
            log.warn("{} stocks share the name '{}'; updating id={} only", matches.size(), name, matches.get(0).getId());
        }
        return matches.stream().findFirst();
    }
}
