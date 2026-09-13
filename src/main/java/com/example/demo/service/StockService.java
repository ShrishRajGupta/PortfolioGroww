package com.example.demo.service;

import com.example.demo.entity.Stock;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface StockService {

    /** Fetches the configured stock sheet and upserts every row by stock name. */
    void downloadAndProcessStockFile();

    /** Upserts stocks (by name) from an uploaded CSV. Returns the number of rows applied. */
    int processCsv(MultipartFile file);

    List<Stock> searchStockByName(String name);

    Optional<Stock> findStockById(Long id);
}
