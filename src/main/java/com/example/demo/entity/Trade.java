package com.example.demo.entity;

import com.example.demo.entity.enums.TradeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One executed trade. Rows are append-only: the trades table is the ledger and the
 * source of truth for positions. Schema: db/migration V1 + V2.
 */
@Data
@Entity
@Table(name = "trades")
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Client-supplied idempotency key; a retried POST with the same key is not booked twice. */
    @Column(name = "client_trade_id", length = 36, unique = true)
    private String clientTradeId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 8)
    private TradeType tradeType;

    @Column(nullable = false)
    private Integer quantity;

    /** Fill price per unit. Money is DECIMAL(19,4), never floating point. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
