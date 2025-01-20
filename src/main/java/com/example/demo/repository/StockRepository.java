package com.example.demo.repository;

import com.example.demo.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    List<Stock> findByName(String name);

//    void save(Stock stock);

    Optional<Stock> findById(Long stockId);

    List<Stock> findByNameContainingIgnoreCase(String trim);
}
