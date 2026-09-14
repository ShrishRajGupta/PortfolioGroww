package com.example.demo.service.Impl;

import com.example.demo.dto.PortfolioHoldingDTO;
import com.example.demo.entity.Stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weighted-average-cost book for one stock, fed trades in chronological order.
 * <ul>
 *   <li>BUY: average cost is re-weighted over the enlarged position.</li>
 *   <li>SELL: realizes (fill - avgCost) x quantity; the average cost of what remains is unchanged.
 *       Selling more than is held (possible in historical data written outside the API) realizes only
 *       the covered part and records the rest as unmatched, so a read never fails on bad history.</li>
 * </ul>
 * Can start empty or be hydrated from a stored position and continue from there — the same
 * arithmetic serves the incremental write path, the read path, the migration backfill and
 * reconciliation. Internal arithmetic keeps 8 decimals; money outputs round HALF_UP to 4.
 */
public final class AverageCostPosition {

    static final int MONEY_SCALE = 4;
    public static final int INTERNAL_SCALE = 8;

    private long netQuantity;
    private BigDecimal avgCost;
    private BigDecimal realizedPnl;
    private long unmatchedSellQuantity;

    public AverageCostPosition() {
        this(0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public AverageCostPosition(long netQuantity, BigDecimal avgCost, BigDecimal realizedPnl) {
        this.netQuantity = netQuantity;
        this.avgCost = internal(avgCost);
        this.realizedPnl = internal(realizedPnl);
    }

    public void applyBuy(int quantity, BigDecimal fillPrice) {
        BigDecimal totalCost = avgCost.multiply(BigDecimal.valueOf(netQuantity))
                .add(fillPrice.multiply(BigDecimal.valueOf(quantity)));
        netQuantity += quantity;
        avgCost = totalCost.divide(BigDecimal.valueOf(netQuantity), INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    public void applySell(int quantity, BigDecimal fillPrice) {
        long covered = Math.min(quantity, netQuantity);
        unmatchedSellQuantity += quantity - covered;
        if (covered == 0) {
            return;
        }
        realizedPnl = internal(realizedPnl.add(fillPrice.subtract(avgCost).multiply(BigDecimal.valueOf(covered))));
        netQuantity -= covered;
        if (netQuantity == 0) {
            avgCost = internal(BigDecimal.ZERO);
        }
    }

    public boolean isOpen() {
        return netQuantity > 0;
    }

    public long getNetQuantity() {
        return netQuantity;
    }

    /** Average cost at internal precision (8 decimals) — what gets persisted. */
    public BigDecimal getAvgCost() {
        return avgCost;
    }

    /** Realized P&amp;L at internal precision (8 decimals) — what gets persisted. */
    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public long getUnmatchedSellQuantity() {
        return unmatchedSellQuantity;
    }

    public BigDecimal getCostBasis() {
        return money(avgCost.multiply(BigDecimal.valueOf(netQuantity)));
    }

    public BigDecimal getMarketValue(Stock stock) {
        return money(stock.getClosePrice().multiply(BigDecimal.valueOf(netQuantity)));
    }

    public PortfolioHoldingDTO toHolding(Stock stock) {
        BigDecimal marketValue = getMarketValue(stock);
        BigDecimal costBasis = getCostBasis();
        return PortfolioHoldingDTO.builder()
                .stockId(stock.getId())
                .stockName(stock.getName())
                .netQuantity((int) netQuantity)
                .avgCost(money(avgCost))
                .marketPrice(money(stock.getClosePrice()))
                .costBasis(costBasis)
                .marketValue(marketValue)
                .unrealizedPnl(marketValue.subtract(costBasis))
                .realizedPnl(money(realizedPnl))
                .build();
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal internal(BigDecimal value) {
        return value.setScale(INTERNAL_SCALE, RoundingMode.HALF_UP);
    }
}
