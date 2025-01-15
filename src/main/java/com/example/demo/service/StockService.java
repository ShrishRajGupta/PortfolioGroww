package com.example.demo.service;

import com.example.demo.entity.Stock;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface StockService {
    void processCsv(MultipartFile file);

    void updateStocksFromCsv(MultipartFile file);

    Optional<Stock> findStockById(Long id);
}
