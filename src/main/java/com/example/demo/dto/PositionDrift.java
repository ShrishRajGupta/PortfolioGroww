package com.example.demo.dto;

import java.math.BigDecimal;

/**
 * One (user, stock) where the stored position disagrees with what the ledger implies.
 * {@code stored*} fields are null when no position row exists for a stock the ledger knows.
 */
public record PositionDrift(
        Long stockId,
        long ledgerNetQuantity,
        Long storedNetQuantity,
        BigDecimal ledgerAvgCost,
        BigDecimal storedAvgCost) {
}
