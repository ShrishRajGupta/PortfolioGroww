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
 * Internal arithmetic keeps 8 decimals; outputs are rounded to 4 (money) HALF_UP.
 */
public final class AverageCostPosition {

    static final int MONEY_SCALE = 4;
    private static final int INTERNAL_SCALE = 8;

    private final Stock stock;
    private long netQuantity;
    private BigDecimal avgCost = BigDecimal.ZERO;
    private BigDecimal realizedPnl = BigDecimal.ZERO;
    private long unmatchedSellQuantity;

    public AverageCostPosition(Stock stock) {
        this.stock = stock;
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
        realizedPnl = realizedPnl.add(fillPrice.subtract(avgCost).multiply(BigDecimal.valueOf(covered)));
        netQuantity -= covered;
        if (netQuantity == 0) {
            avgCost = BigDecimal.ZERO;
        }
    }

    public boolean isOpen() {
        return netQuantity > 0;
    }

    public long getNetQuantity() {
        return netQuantity;
    }

    public long getUnmatchedSellQuantity() {
        return unmatchedSellQuantity;
    }

    public BigDecimal getRealizedPnl() {
        return money(realizedPnl);
    }

    public BigDecimal getCostBasis() {
        return money(avgCost.multiply(BigDecimal.valueOf(netQuantity)));
    }

    public BigDecimal getMarketValue() {
        return money(stock.getClosePrice().multiply(BigDecimal.valueOf(netQuantity)));
    }

    public BigDecimal getUnrealizedPnl() {
        return getMarketValue().subtract(getCostBasis());
    }

    public PortfolioHoldingDTO toHolding() {
        return PortfolioHoldingDTO.builder()
                .stockId(stock.getId())
                .stockName(stock.getName())
                .netQuantity((int) netQuantity)
                .avgCost(money(avgCost))
                .marketPrice(money(stock.getClosePrice()))
                .costBasis(getCostBasis())
                .marketValue(getMarketValue())
                .unrealizedPnl(getUnrealizedPnl())
                .realizedPnl(getRealizedPnl())
                .build();
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
