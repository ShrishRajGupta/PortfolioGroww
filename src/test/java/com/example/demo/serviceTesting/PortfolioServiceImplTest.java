package com.example.demo.serviceTesting;

import com.example.demo.dto.PortfolioHoldingDTO;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.entity.Position;
import com.example.demo.entity.Stock;
import com.example.demo.repository.PositionRepository;
import com.example.demo.service.Impl.PortfolioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Valuation of materialized positions; the arithmetic itself is covered in AverageCostPositionTest. */
class PortfolioServiceImplTest {

    @Mock
    private PositionRepository positionRepository;

    @InjectMocks
    private PortfolioServiceImpl portfolioService;

    private static final Stock ACME = stock(1L, "ACME", "120");
    private static final Stock BOLT = stock(2L, "BOLT", "50");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static Stock stock(long id, String name, String close) {
        BigDecimal c = new BigDecimal(close);
        return new Stock(id, name, c, c, c, c, c);
    }

    private static Position position(long id, Stock s, long net, String avgCost, String realized) {
        return Position.builder().id(id).stock(s).netQuantity(net)
                .avgCost(new BigDecimal(avgCost)).realizedPnl(new BigDecimal(realized)).build();
    }

    private PortfolioResponseDTO portfolioOf(Position... positions) {
        when(positionRepository.findAllForUser(1L)).thenReturn(List.of(positions));
        return portfolioService.getPortfolio(1L);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void openPosition_isValuedAtClosePrice() {
        PortfolioResponseDTO p = portfolioOf(position(1, ACME, 6, "100.00000000", "120.00000000"));

        assertEquals(1, p.getHoldings().size());
        PortfolioHoldingDTO h = p.getHoldings().get(0);
        assertEquals(6, h.getNetQuantity());
        assertEquals(bd("100.0000"), h.getAvgCost());
        assertEquals(bd("120.0000"), h.getMarketPrice());
        assertEquals(bd("600.0000"), h.getCostBasis());
        assertEquals(bd("720.0000"), h.getMarketValue());
        assertEquals(bd("120.0000"), h.getUnrealizedPnl());
        assertEquals(bd("120.0000"), h.getRealizedPnl());
        assertEquals(bd("240.0000"), p.getTotalPnl());
        assertEquals(bd("20.00"), p.getUnrealizedReturnPercentage());
    }

    @Test
    void closedPosition_dropsOutOfHoldings_butKeepsRealizedInTotals() {
        PortfolioResponseDTO p = portfolioOf(
                position(1, ACME, 0, "0", "-50.00000000"),
                position(2, BOLT, 2, "40.00000000", "0"));

        assertEquals(List.of("BOLT"), p.getHoldings().stream().map(PortfolioHoldingDTO::getStockName).toList());
        assertEquals(bd("-50.0000"), p.getTotalRealizedPnl());
        assertEquals(bd("80.0000"), p.getTotalCostBasis());
        assertEquals(bd("100.0000"), p.getTotalMarketValue());
        assertEquals(bd("20.0000"), p.getTotalUnrealizedPnl());
        assertEquals(bd("-30.0000"), p.getTotalPnl());
        assertEquals(bd("25.00"), p.getUnrealizedReturnPercentage());
    }

    @Test
    void holdings_keepRepositoryOrder() {
        PortfolioResponseDTO p = portfolioOf(
                position(1, BOLT, 8, "50.00000000", "0"),
                position(2, ACME, 1, "100.00000000", "0"));

        assertEquals(List.of("BOLT", "ACME"), p.getHoldings().stream().map(PortfolioHoldingDTO::getStockName).toList());
        assertEquals(bd("520.0000"), p.getTotalMarketValue(), "400 + 120");
    }

    @Test
    void internalPrecision_isRoundedHalfUpForOutput() {
        PortfolioResponseDTO p = portfolioOf(position(1, BOLT, 10, "10.03000000", "0.00004999"));

        PortfolioHoldingDTO h = p.getHoldings().get(0);
        assertEquals(bd("10.0300"), h.getAvgCost());
        assertEquals(bd("100.3000"), h.getCostBasis());
        assertEquals(bd("399.7000"), h.getUnrealizedPnl());
        assertEquals(bd("0.0000"), h.getRealizedPnl(), "0.00004999 rounds down at 4 dp");
        assertEquals(bd("398.50"), p.getUnrealizedReturnPercentage());
    }

    @Test
    void noPositions_returnsZeroesWithConsistentScale() {
        PortfolioResponseDTO p = portfolioOf();

        assertTrue(p.getHoldings().isEmpty());
        assertEquals(bd("0.0000"), p.getTotalMarketValue());
        assertEquals(bd("0.0000"), p.getTotalCostBasis());
        assertEquals(bd("0.0000"), p.getTotalUnrealizedPnl());
        assertEquals(bd("0.0000"), p.getTotalRealizedPnl());
        assertEquals(bd("0.0000"), p.getTotalPnl());
        assertEquals(bd("0.00"), p.getUnrealizedReturnPercentage());
    }

    @Test
    void usesTheSinglePositionsQuery_only() {
        portfolioOf(position(1, ACME, 1, "1", "0"));

        verify(positionRepository, times(1)).findAllForUser(1L);
        verifyNoMoreInteractions(positionRepository);
    }
}
