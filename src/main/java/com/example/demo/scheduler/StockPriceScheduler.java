package com.example.demo.scheduler;

import com.example.demo.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes stock prices from the published sheet.
 * Lives outside the web layer so the controller stays request-only.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StockPriceScheduler {

    private final StockService stockService;

    @Scheduled(cron = "${app.stock-price-cron}")
    public void refreshStockPrices() {
        log.info("Scheduled stock price refresh starting");
        stockService.downloadAndProcessStockFile();
    }
}
