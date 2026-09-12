package com.example.demo.repository;

import com.example.demo.entity.Trade;
import com.example.demo.entity.enums.TradeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserAccountId(Long userAccountId);

    /** Idempotency lookup; backed by the unique index on client_trade_id. */
    Optional<Trade> findByClientTradeId(String clientTradeId);

    /**
     * Every trade of a user in booking order with the stock fetched in the same query —
     * one round-trip for the whole portfolio. The id tie-break keeps ordering deterministic
     * for trades that share a timestamp.
     */
    @Query("""
            select t from Trade t
            join fetch t.stock
            join fetch t.userAccount
            where t.userAccount.id = :userId
            order by t.createdAt asc, t.id asc
            """)
    List<Trade> findAllForPortfolio(@Param("userId") Long userId);

    @Query("""
            select coalesce(sum(case when t.tradeType = :buy then t.quantity else (0 - t.quantity) end), 0)
            from Trade t
            where t.userAccount.id = :userId and t.stock.id = :stockId
            """)
    long netPosition(@Param("userId") Long userId, @Param("stockId") Long stockId, @Param("buy") TradeType buy);

    /** Units of a stock a user currently holds: BUY quantity minus SELL quantity. */
    default long netPosition(Long userId, Long stockId) {
        return netPosition(userId, stockId, TradeType.BUY);
    }
}
