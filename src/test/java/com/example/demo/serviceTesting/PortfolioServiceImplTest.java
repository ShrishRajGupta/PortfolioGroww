package com.example.demo.serviceTesting;

import com.example.demo.dto.PortfolioHoldingDTO;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.repository.TradeRepository;
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

/** The weighted-average-cost book, fed trades in the order the repository returns them. */
class PortfolioServiceImplTest {

    @Mock
    private TradeRepository tradeRepository;

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

    private static Trade buy(Stock s, int qty, String price) {
        return Trade.builder().stock(s).tradeType(TradeType.BUY).quantity(qty).price(new BigDecimal(price)).build();
    }

    private static Trade sell(Stock s, int qty, String price) {
        return Trade.builder().stock(s).tradeType(TradeType.SELL).quantity(qty).price(new BigDecimal(price)).build();
    }

    private PortfolioResponseDTO portfolioOf(Trade... trades) {
        when(tradeRepository.findAllForPortfolio(1L)).thenReturn(List.of(trades));
        return portfolioService.getPortfolio(1L);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void singleBuy_isValuedAtClosePrice() {
        PortfolioResponseDTO p = portfolioOf(buy(ACME, 10, "100"));

        assertEquals(1, p.getHoldings().size());
        PortfolioHoldingDTO h = p.getHoldings().get(0);
        assertEquals(10, h.getNetQuantity());
        assertEquals(bd("100.0000"), h.getAvgCost());
        assertEquals(bd("120.0000"), h.getMarketPrice());
        assertEquals(bd("1000.0000"), h.getCostBasis());
        assertEquals(bd("1200.0000"), h.getMarketValue());
        assertEquals(bd("200.0000"), h.getUnrealizedPnl());
        assertEquals(bd("0.0000"), h.getRealizedPnl());

        assertEquals(bd("1200.0000"), p.getTotalMarketValue());
        assertEquals(bd("1000.0000"), p.getTotalCostBasis());
        assertEquals(bd("200.0000"), p.getTotalUnrealizedPnl());
        assertEquals(bd("0.0000"), p.getTotalRealizedPnl());
        assertEquals(bd("200.0000"), p.getTotalPnl());
        assertEquals(bd("20.00"), p.getUnrealizedReturnPercentage());
    }

    @Test
    void repeatedBuys_reweightAverageCost() {
        // 10 @ 100 + 10 @ 140 -> 20 @ 120
        PortfolioResponseDTO p = portfolioOf(buy(ACME, 10, "100"), buy(ACME, 10, "140"));

        PortfolioHoldingDTO h = p.getHoldings().get(0);
        assertEquals(20, h.getNetQuantity());
        assertEquals(bd("120.0000"), h.getAvgCost());
        assertEquals(bd("0.0000"), h.getUnrealizedPnl(), "market 120 == avg cost 120");
    }

    @Test
    void partialSell_realizesAgainstAverageCost_andLeavesAvgCostUnchanged() {
        // buy 10 @ 100, sell 4 @ 130 -> realized 4 x 30 = 120; 6 left @ avg 100
        PortfolioResponseDTO p = portfolioOf(buy(ACME, 10, "100"), sell(ACME, 4, "130"));

        PortfolioHoldingDTO h = p.getHoldings().get(0);
        assertEquals(6, h.getNetQuantity());
        assertEquals(bd("100.0000"), h.getAvgCost());
        assertEquals(bd("120.0000"), h.getRealizedPnl());
        assertEquals(bd("120.0000"), h.getUnrealizedPnl(), "6 x (120 - 100)");
        assertEquals(bd("120.0000"), p.getTotalRealizedPnl());
        assertEquals(bd("240.0000"), p.getTotalPnl());
    }

    @Test
    void fullyClosedPosition_dropsOutOfHoldings_butKeepsRealizedInTotals() {
        PortfolioResponseDTO p = portfolioOf(buy(ACME, 5, "100"), sell(ACME, 5, "90"), buy(BOLT, 2, "40"));

        assertEquals(List.of("BOLT"), p.getHoldings().stream().map(PortfolioHoldingDTO::getStockName).toList());
        assertEquals(bd("-50.0000"), p.getTotalRealizedPnl(), "5 x (90 - 100)");
        assertEquals(bd("80.0000"), p.getTotalCostBasis(), "only BOLT is open");
        assertEquals(bd("100.0000"), p.getTotalMarketValue());
        assertEquals(bd("20.0000"), p.getTotalUnrealizedPnl());
        assertEquals(bd("-30.0000"), p.getTotalPnl());
        assertEquals(bd("25.00"), p.getUnrealizedReturnPercentage());
    }

    @Test
    void buyAfterClosing_startsAFreshCostBasis() {
        // realized from the first round-trip must not leak into the new position's avg cost
        PortfolioResponseDTO p = portfolioOf(buy(ACME, 5, "100"), sell(ACME, 5, "150"), buy(ACME, 2, "200"));

        PortfolioHoldingDTO h = p.getHoldings().get(0);
        assertEquals(2, h.getNetQuantity());
        assertEquals(bd("200.0000"), h.getAvgCost());
        assertEquals(bd("250.0000"), h.getRealizedPnl());
        assertEquals(bd("-160.0000"), h.getUnrealizedPnl(), "2 x (120 - 200)");
    }

    @Test
    void orderMatters_sellRealizesAgainstCostAtThatMoment() {
        // sell happens before the expensive buy, so it realizes against 100 not the later average
        PortfolioResponseDTO p = portfolioOf(buy(ACME, 10, "100"), sell(ACME, 10, "110"), buy(ACME, 10, "300"));

        PortfolioHoldingDTO h = p.getHoldings().get(0);
        assertEquals(bd("100.0000"), h.getRealizedPnl(), "10 x (110 - 100)");
        assertEquals(bd("300.0000"), h.getAvgCost());
    }

    @Test
    void sellBeyondPosition_realizesCoveredPartOnly_andDoesNotFail() {
        // historical ledger written outside the API: sell 8 while holding 5
        PortfolioResponseDTO p = portfolioOf(buy(ACME, 5, "100"), sell(ACME, 8, "110"));

        assertTrue(p.getHoldings().isEmpty());
        assertEquals(bd("50.0000"), p.getTotalRealizedPnl(), "5 covered x (110 - 100)");
        assertEquals(bd("0.0000"), p.getTotalCostBasis());
    }

    @Test
    void sellWithNoPositionAtAll_isIgnored() {
        PortfolioResponseDTO p = portfolioOf(sell(ACME, 3, "110"));

        assertTrue(p.getHoldings().isEmpty());
        assertEquals(bd("0.0000"), p.getTotalRealizedPnl());
        assertEquals(bd("0.00"), p.getUnrealizedReturnPercentage());
    }

    @Test
    void multipleStocks_areAggregatedIndependently_inFirstSeenOrder() {
        PortfolioResponseDTO p = portfolioOf(buy(BOLT, 4, "45"), buy(ACME, 1, "100"), buy(BOLT, 4, "55"));

        assertEquals(List.of("BOLT", "ACME"), p.getHoldings().stream().map(PortfolioHoldingDTO::getStockName).toList());
        PortfolioHoldingDTO bolt = p.getHoldings().get(0);
        assertEquals(8, bolt.getNetQuantity());
        assertEquals(bd("50.0000"), bolt.getAvgCost());
        assertEquals(bd("400.0000"), bolt.getMarketValue());
        assertEquals(bd("520.0000"), p.getTotalMarketValue(), "400 + 120");
    }

    @Test
    void emptyLedger_returnsZeroesWithConsistentScale() {
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
    void averageCostKeepsPrecision_thenRoundsHalfUpForOutput() {
        // 3 @ 10 + 1 @ 11 -> avg 10.25 ; 7 @ 10 + 3 @ 10.1 -> 10.03
        PortfolioResponseDTO p = portfolioOf(buy(BOLT, 7, "10"), buy(BOLT, 3, "10.1"));

        PortfolioHoldingDTO h = p.getHoldings().get(0);
        assertEquals(bd("10.0300"), h.getAvgCost());
        assertEquals(bd("100.3000"), h.getCostBasis());
        assertEquals(bd("500.0000"), h.getMarketValue());
        assertEquals(bd("399.7000"), h.getUnrealizedPnl());
        assertEquals(bd("398.50"), p.getUnrealizedReturnPercentage(), "399.7 / 100.3 x 100");
    }

    @Test
    void usesTheSingleFetchJoinQuery_only() {
        portfolioOf(buy(ACME, 1, "1"));

        verify(tradeRepository, times(1)).findAllForPortfolio(1L);
        verifyNoMoreInteractions(tradeRepository);
    }
}
