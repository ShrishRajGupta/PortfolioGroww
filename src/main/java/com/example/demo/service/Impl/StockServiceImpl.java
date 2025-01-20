package com.example.demo.service.Impl;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    @Autowired
    private final StockRepository stockRepository;

    @Override
    public void processCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                Stock stock = new Stock();
                stock.setName(fields[0]);
                stock.setOpenPrice(Double.parseDouble(fields[1]));
                stock.setClosePrice(Double.parseDouble(fields[2]));
                stock.setHighPrice(Double.parseDouble(fields[3]));
                stock.setLowPrice(Double.parseDouble(fields[4]));
                stock.setSettlementPrice(Double.parseDouble(fields[5]));
                stockRepository.save(stock);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process CSV", e);
        }
    }

    @Override
    public List<Stock> searchStockByName(String name){
        return stockRepository.findByNameContainingIgnoreCase(name.trim());
    }

    @Override
    public Optional<Stock> findStockById(Long id){
        return stockRepository.findById(id);
    }

    @Override
    public void updateStocksFromCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");

                if (fields.length >= 6) {
                    Stock stock = new Stock();
                    stock.setName(fields[0].trim());
                    stock.setOpenPrice(Double.parseDouble(fields[1].trim()));
                    stock.setClosePrice(Double.parseDouble(fields[2].trim()));
                    stock.setHighPrice(Double.parseDouble(fields[3].trim()));
                    stock.setLowPrice(Double.parseDouble(fields[4].trim()));
                    stock.setSettlementPrice(Double.parseDouble(fields[5].trim()));

                    stockRepository.save(stock);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process CSV file", e);
        }
    }
}