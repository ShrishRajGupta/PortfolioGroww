package com.example.demo.serviceTesting;

import com.example.demo.dto.PositionDrift;
import com.example.demo.dto.RebuildResult;
import com.example.demo.entity.Position;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.exception.InsufficientPositionException;
import com.example.demo.repository.PositionRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.Impl.PositionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PositionServiceImplTest {

    @Mock
    private PositionRepository positionRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private PositionServiceImpl positionService;

    private final UserAccount user = new UserAccount(1L, "User1", "user1@example.com", null);
    private final Stock acme = stock(1L, "ACME", "120");
    private final Stock bolt = stock(2L, "BOLT", "50");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(positionRepository.save(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Stock stock(long id, String name, String close) {
        BigDecimal c = new BigDecimal(close);
        return new Stock(id, name, c, c, c, c, c);
    }

    private Trade trade(Stock s, TradeType t, int qty, String price) {
        return Trade.builder().userAccount(user).stock(s).tradeType(t).quantity(qty).price(new BigDecimal(price)).build();
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void firstBuy_createsThePositionRow() {
        when(positionRepository.findByUserAccountIdAndStockId(1L, 1L)).thenReturn(Optional.empty());

        Position p = positionService.applyTrade(user, acme, TradeType.BUY, 10, bd("100"));

        assertSame(user, p.getUserAccount());
        assertSame(acme, p.getStock());
        assertEquals(10, p.getNetQuantity());
        assertEquals(bd("100.00000000"), p.getAvgCost());
        assertEquals(bd("0.00000000"), p.getRealizedPnl());
        verify(positionRepository).save(p);
    }

    @Test
    void laterTrade_updatesTheExistingRowInPlace() {
        Position existing = Position.builder().id(5L).userAccount(user).stock(acme)
                .netQuantity(10).avgCost(bd("100.00000000")).realizedPnl(bd("0")).version(3L).build();
        when(positionRepository.findByUserAccountIdAndStockId(1L, 1L)).thenReturn(Optional.of(existing));

        Position p = positionService.applyTrade(user, acme, TradeType.SELL, 4, bd("130"));

        assertSame(existing, p, "same managed entity, so @Version applies on flush");
        assertEquals(6, p.getNetQuantity());
        assertEquals(bd("100.00000000"), p.getAvgCost());
        assertEquals(bd("120.00000000"), p.getRealizedPnl());
        assertEquals(3L, p.getVersion(), "version is bumped by Hibernate at flush, not by us");
    }

    @Test
    void sellBeyondHeld_throwsWithoutSaving() {
        Position existing = Position.builder().userAccount(user).stock(acme).netQuantity(4)
                .avgCost(bd("100")).realizedPnl(bd("0")).build();
        when(positionRepository.findByUserAccountIdAndStockId(1L, 1L)).thenReturn(Optional.of(existing));

        InsufficientPositionException ex = assertThrows(InsufficientPositionException.class,
                () -> positionService.applyTrade(user, acme, TradeType.SELL, 5, bd("130")));

        assertEquals("Cannot sell 5 of stock 1: only 4 held", ex.getMessage());
        verify(positionRepository, never()).save(any());
    }

    @Test
    void sellWithNoRow_throws() {
        when(positionRepository.findByUserAccountIdAndStockId(1L, 2L)).thenReturn(Optional.empty());

        assertThrows(InsufficientPositionException.class,
                () -> positionService.applyTrade(user, bolt, TradeType.SELL, 1, bd("1")));
        verify(positionRepository, never()).save(any());
    }

    @Test
    void rebuild_replacesRowsWithLedgerReplay() {
        when(tradeRepository.findAllForPortfolio(1L)).thenReturn(List.of(
                trade(acme, TradeType.BUY, 10, "100"),
                trade(acme, TradeType.SELL, 4, "130"),
                trade(bolt, TradeType.BUY, 2, "40")));

        RebuildResult result = positionService.rebuild(1L);

        assertEquals(new RebuildResult(1, 2), result);
        verify(positionRepository).deleteByUserAccountId(1L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Position>> saved = ArgumentCaptor.forClass(List.class);
        verify(positionRepository).saveAll(saved.capture());
        List<Position> rows = saved.getValue();
        assertEquals(2, rows.size());
        assertEquals(6, rows.get(0).getNetQuantity());
        assertEquals(bd("100.00000000"), rows.get(0).getAvgCost());
        assertEquals(bd("120.00000000"), rows.get(0).getRealizedPnl());
        assertSame(bolt, rows.get(1).getStock());
        assertEquals(2, rows.get(1).getNetQuantity());
    }

    @Test
    void rebuildAll_visitsEveryUser() {
        UserAccount other = new UserAccount(2L, "User2", "user2@example.com", null);
        when(userAccountRepository.findAll()).thenReturn(List.of(user, other));
        when(tradeRepository.findAllForPortfolio(1L)).thenReturn(List.of(trade(acme, TradeType.BUY, 1, "1")));
        when(tradeRepository.findAllForPortfolio(2L)).thenReturn(List.of());

        RebuildResult result = positionService.rebuildAll();

        assertEquals(new RebuildResult(2, 1), result);
        verify(positionRepository).deleteByUserAccountId(1L);
        verify(positionRepository).deleteByUserAccountId(2L);
        verify(positionRepository, times(2)).saveAll(anyList());
    }

    @Test
    void drift_isEmptyWhenPositionsMatchTheLedger() {
        when(tradeRepository.findAllForPortfolio(1L)).thenReturn(List.of(
                trade(acme, TradeType.BUY, 10, "100"), trade(acme, TradeType.SELL, 4, "130")));
        when(positionRepository.findAllForUser(1L)).thenReturn(List.of(
                Position.builder().stock(acme).netQuantity(6).avgCost(bd("100.00000000")).realizedPnl(bd("120.00000000")).build()));

        assertTrue(positionService.drift(1L).isEmpty());
    }

    @Test
    void drift_reportsMismatch_missingRow_andOrphanRow() {
        when(tradeRepository.findAllForPortfolio(1L)).thenReturn(List.of(
                trade(acme, TradeType.BUY, 10, "100"),
                trade(bolt, TradeType.BUY, 3, "40")));
        Stock ghost = stock(3L, "GHOST", "1");
        when(positionRepository.findAllForUser(1L)).thenReturn(List.of(
                Position.builder().stock(acme).netQuantity(9).avgCost(bd("100.00000000")).realizedPnl(bd("0")).build(),
                Position.builder().stock(ghost).netQuantity(1).avgCost(bd("5.00000000")).realizedPnl(bd("0")).build()));

        List<PositionDrift> drifts = positionService.drift(1L);

        assertEquals(3, drifts.size());
        PositionDrift acmeDrift = drifts.get(0);
        assertEquals(1L, acmeDrift.stockId());
        assertEquals(10, acmeDrift.ledgerNetQuantity());
        assertEquals(9L, acmeDrift.storedNetQuantity());
        PositionDrift missingBolt = drifts.get(1);
        assertEquals(2L, missingBolt.stockId());
        assertNull(missingBolt.storedNetQuantity());
        PositionDrift orphan = drifts.get(2);
        assertEquals(3L, orphan.stockId());
        assertEquals(0, orphan.ledgerNetQuantity());
        assertEquals(1L, orphan.storedNetQuantity());
    }
}
