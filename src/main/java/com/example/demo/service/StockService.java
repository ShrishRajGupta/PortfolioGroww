package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class StockService {

    @Autowired
    private StockRepository stockRepository;

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
}