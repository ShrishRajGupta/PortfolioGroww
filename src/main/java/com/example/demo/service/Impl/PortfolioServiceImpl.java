package com.example.demo.service.Impl;

import com.example.demo.dto.PortfolioHoldingDTO;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.entity.Trade;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.repository.TradeRepository;
import com.example.demo.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.demo.service.Impl.AverageCostPosition.money;

/**
 * Values a portfolio straight from the trade ledger: one query (trades with their stocks, in
 * booking order), one pass through the trades building an {@link AverageCostPosition} per stock.
 * O(trades) with no per-trade round-trips. A materialized positions table can replace this read
 * path later without changing the response.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TradeRepository tradeRepository;

    @Override
    @Transactional(readOnly = true)
    public PortfolioResponseDTO getPortfolio(Long userId) {
        List<Trade> trades = tradeRepository.findAllForPortfolio(userId);

        Map<Long, AverageCostPosition> book = new LinkedHashMap<>();
        for (Trade trade : trades) {
            AverageCostPosition position = book.computeIfAbsent(trade.getStock().getId(),
                    id -> new AverageCostPosition(trade.getStock()));
            if (trade.getTradeType() == TradeType.BUY) {
                position.applyBuy(trade.getQuantity(), trade.getPrice());
            } else {
                position.applySell(trade.getQuantity(), trade.getPrice());
            }
        }

        List<PortfolioHoldingDTO> holdings = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalRealized = BigDecimal.ZERO;
        long unmatched = 0;

        for (AverageCostPosition position : book.values()) {
            totalRealized = totalRealized.add(position.getRealizedPnl());
            unmatched += position.getUnmatchedSellQuantity();
            if (position.isOpen()) {
                holdings.add(position.toHolding());
                totalMarketValue = totalMarketValue.add(position.getMarketValue());
                totalCostBasis = totalCostBasis.add(position.getCostBasis());
            }
        }
        if (unmatched > 0) {
            log.warn("Portfolio of user {} ignored {} SELL units not covered by prior BUYs (ledger written outside the API?)",
                    userId, unmatched);
        }

        BigDecimal totalUnrealized = totalMarketValue.subtract(totalCostBasis);
        BigDecimal returnPct = totalCostBasis.signum() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalUnrealized.multiply(HUNDRED).divide(totalCostBasis, 2, RoundingMode.HALF_UP);

        return PortfolioResponseDTO.builder()
                .holdings(holdings)
                .totalMarketValue(money(totalMarketValue))
                .totalCostBasis(money(totalCostBasis))
                .totalUnrealizedPnl(money(totalUnrealized))
                .totalRealizedPnl(money(totalRealized))
                .totalPnl(money(totalUnrealized.add(totalRealized)))
                .unrealizedReturnPercentage(returnPct)
                .build();
    }
}
