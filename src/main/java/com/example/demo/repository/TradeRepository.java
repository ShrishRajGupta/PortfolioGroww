package com.example.demo.repository;

import com.example.demo.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserAccountId(Long userAccountId);

    /** Idempotency lookup; backed by the unique index on client_trade_id. */
    Optional<Trade> findByClientTradeId(String clientTradeId);
}
