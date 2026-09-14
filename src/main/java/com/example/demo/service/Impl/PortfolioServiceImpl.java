package com.example.demo.service.Impl;

import com.example.demo.dto.PortfolioHoldingDTO;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.entity.Position;
import com.example.demo.repository.PositionRepository;
import com.example.demo.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static com.example.demo.service.Impl.AverageCostPosition.money;

/**
 * Values a portfolio from the materialized positions: one query (positions with their stocks),
 * O(holdings) work. Open positions become holdings; realized P&L of closed positions stays in the
 * totals. The ledger remains the source of truth — see PositionService for rebuild/drift.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PositionRepository positionRepository;

    @Override
    @Transactional(readOnly = true)
    public PortfolioResponseDTO getPortfolio(Long userId) {
        List<Position> positions = positionRepository.findAllForUser(userId);

        List<PortfolioHoldingDTO> holdings = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalRealized = BigDecimal.ZERO;

        for (Position position : positions) {
            AverageCostPosition book = new AverageCostPosition(
                    position.getNetQuantity(), position.getAvgCost(), position.getRealizedPnl());
            totalRealized = totalRealized.add(money(book.getRealizedPnl()));
            if (book.isOpen()) {
                PortfolioHoldingDTO holding = book.toHolding(position.getStock());
                holdings.add(holding);
                totalMarketValue = totalMarketValue.add(holding.getMarketValue());
                totalCostBasis = totalCostBasis.add(holding.getCostBasis());
            }
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
