package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Materialized holding of one user in one stock, kept in step with the trade ledger inside the
 * same transaction as each trade. {@code version} gives optimistic locking: two trades updating
 * the same position concurrently make the second one fail and retry. Schema: db/migration V3.
 */
@Data
@Entity
@Table(name = "positions", uniqueConstraints = @UniqueConstraint(name = "uk_positions_user_stock",
        columnNames = {"user_account_id", "stock_id"}))
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    /** Units held: BUY quantity minus SELL quantity. Never negative. */
    @Column(name = "net_quantity", nullable = false)
    private long netQuantity;

    /** Weighted-average cost per unit, kept at 8 decimals so incremental updates don't drift. */
    @Column(name = "avg_cost", nullable = false, precision = 19, scale = 8)
    private BigDecimal avgCost;

    @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 8)
    private BigDecimal realizedPnl;

    @Version
    @Column(nullable = false)
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
