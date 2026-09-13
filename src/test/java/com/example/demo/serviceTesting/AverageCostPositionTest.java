package com.example.demo.serviceTesting;

import com.example.demo.service.Impl.AverageCostPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** The book arithmetic shared by the write path, the read path, the V3.1 backfill and reconciliation. */
class AverageCostPositionTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void buysReweightAverageCost() {
        AverageCostPosition p = new AverageCostPosition();
        p.applyBuy(10, bd("100"));
        p.applyBuy(10, bd("140"));

        assertEquals(20, p.getNetQuantity());
        assertEquals(bd("120.00000000"), p.getAvgCost());
        assertEquals(bd("0.00000000"), p.getRealizedPnl());
    }

    @Test
    void sellRealizesAgainstAverageCost_andLeavesItUnchanged() {
        AverageCostPosition p = new AverageCostPosition();
        p.applyBuy(10, bd("100"));
        p.applySell(4, bd("130"));

        assertEquals(6, p.getNetQuantity());
        assertEquals(bd("100.00000000"), p.getAvgCost());
        assertEquals(bd("120.00000000"), p.getRealizedPnl());
    }

    @Test
    void closingResetsAverageCost_andANewBuyStartsFresh() {
        AverageCostPosition p = new AverageCostPosition();
        p.applyBuy(5, bd("100"));
        p.applySell(5, bd("150"));
        assertFalse(p.isOpen());
        assertEquals(bd("0.00000000"), p.getAvgCost());

        p.applyBuy(2, bd("200"));
        assertEquals(bd("200.00000000"), p.getAvgCost());
        assertEquals(bd("250.00000000"), p.getRealizedPnl());
    }

    @Test
    void oversellInHistory_realizesCoveredPartAndCountsTheRest() {
        AverageCostPosition p = new AverageCostPosition();
        p.applyBuy(5, bd("100"));
        p.applySell(8, bd("110"));

        assertEquals(0, p.getNetQuantity());
        assertEquals(3, p.getUnmatchedSellQuantity());
        assertEquals(bd("50.00000000"), p.getRealizedPnl());
    }

    @Test
    void hydratedFromStoredState_continuesExactlyLikeAFullReplay() {
        // replay everything from scratch
        AverageCostPosition replay = new AverageCostPosition();
        replay.applyBuy(7, bd("107"));
        replay.applyBuy(2, bd("90.1234"));
        replay.applySell(3, bd("200"));

        // vs. hydrate from what the first two trades left in the positions row, then apply the third
        AverageCostPosition stored = new AverageCostPosition();
        stored.applyBuy(7, bd("107"));
        stored.applyBuy(2, bd("90.1234"));
        AverageCostPosition hydrated = new AverageCostPosition(stored.getNetQuantity(), stored.getAvgCost(), stored.getRealizedPnl());
        hydrated.applySell(3, bd("200"));

        assertEquals(replay.getNetQuantity(), hydrated.getNetQuantity());
        assertEquals(replay.getAvgCost(), hydrated.getAvgCost());
        assertEquals(replay.getRealizedPnl(), hydrated.getRealizedPnl());
        assertEquals(bd("103.24964444"), hydrated.getAvgCost());
        assertEquals(bd("290.25106668"), hydrated.getRealizedPnl());
    }

    @Test
    void internalPrecisionIsEightDecimals_outputsAreFour() {
        AverageCostPosition p = new AverageCostPosition();
        p.applyBuy(3, bd("10"));
        p.applyBuy(1, bd("10.0001"));

        assertEquals(bd("10.00002500"), p.getAvgCost());
        assertEquals(bd("40.0001"), p.getCostBasis());
    }
}
