package com.example.demo.serviceTesting;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Position;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.exception.ConcurrentTradeException;
import com.example.demo.exception.InsufficientPositionException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.Impl.TradeServiceImpl;
import com.example.demo.service.PositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TradeServiceImplTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private PositionService positionService;

    private TradeServiceImpl tradeService;

    private final UserAccount user = new UserAccount(1L, "User1", "user1@example.com", null);
    private final Stock stock = new Stock(1L, "Stock1",
            new BigDecimal("100.0000"), new BigDecimal("105.5000"), new BigDecimal("110.0000"),
            new BigDecimal("95.0000"), new BigDecimal("102.5000"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Real "no transaction" implementation: attempts run inline, exceptions propagate unchanged.
        tradeService = new TradeServiceImpl(tradeRepository, stockRepository, userAccountRepository,
                positionService, TransactionOperations.withoutTransaction(), MAX_ATTEMPTS, 0);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(positionService.applyTrade(any(), any(), any(), anyInt(), any())).thenReturn(new Position());
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> {
            Trade t = inv.getArgument(0);
            t.setId(42L);
            return t;
        });
    }

    private Trade savedTrade() {
        ArgumentCaptor<Trade> saved = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void buy_appliesToPositionThenAppendsLedger_atCurrentClose() {
        TradeResponseDTO response = tradeService.recordTrade(new TradeRequestDTO(1L, 1L, TradeType.BUY, 10));

        assertEquals(42L, response.getTradeId());
        assertEquals("SUCCESS", response.getStatus());
        verify(positionService).applyTrade(user, stock, TradeType.BUY, 10, stock.getClosePrice());
        Trade t = savedTrade();
        assertEquals(TradeType.BUY, t.getTradeType());
        assertEquals(stock.getClosePrice(), t.getPrice());
        assertNull(t.getClientTradeId());
    }

    @Test
    void executionPrice_isUsedForBothPositionAndLedger() {
        tradeService.recordTrade(new TradeRequestDTO(null, 1L, 1L, TradeType.BUY, 3, new BigDecimal("101.2500")));

        verify(positionService).applyTrade(eq(user), eq(stock), eq(TradeType.BUY), eq(3), eq(new BigDecimal("101.2500")));
        assertEquals(new BigDecimal("101.2500"), savedTrade().getPrice());
    }

    @Test
    void oversell_fromPositionService_propagatesAndBooksNothing() {
        when(positionService.applyTrade(any(), any(), eq(TradeType.SELL), anyInt(), any()))
                .thenThrow(new InsufficientPositionException(1L, 4, 5));

        assertThrows(InsufficientPositionException.class,
                () -> tradeService.recordTrade(new TradeRequestDTO(1L, 1L, TradeType.SELL, 5)));

        verify(tradeRepository, never()).save(any());
    }

    @Test
    void unknownStock_throws404AndBooksNothing() {
        when(stockRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> tradeService.recordTrade(new TradeRequestDTO(1L, 999L, TradeType.BUY, 10)));

        assertEquals("Stock 999 not found", ex.getMessage());
        verifyNoInteractions(positionService);
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void unknownUser_throws404() {
        when(userAccountRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tradeService.recordTrade(new TradeRequestDTO(77L, 1L, TradeType.SELL, 1)));
        verifyNoInteractions(positionService);
    }

    @Test
    void replayWithSameClientTradeId_returnsOriginalWithoutBookingAgain() {
        Trade original = Trade.builder().id(7L).clientTradeId("abc-123").build();
        when(tradeRepository.findByClientTradeId("abc-123")).thenReturn(Optional.of(original));

        TradeResponseDTO response = tradeService.recordTrade(
                new TradeRequestDTO("abc-123", 1L, 1L, TradeType.BUY, 10, null));

        assertEquals(7L, response.getTradeId());
        assertEquals("Trade already recorded", response.getMessage());
        verify(tradeRepository, never()).save(any());
        verifyNoInteractions(positionService, userAccountRepository, stockRepository);
    }

    @Test
    void optimisticLockCollision_isRetried_thenSucceeds() {
        when(positionService.applyTrade(any(), any(), any(), anyInt(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Position.class, 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Position.class, 1L))
                .thenReturn(new Position());

        TradeResponseDTO response = tradeService.recordTrade(new TradeRequestDTO(1L, 1L, TradeType.BUY, 1));

        assertEquals(42L, response.getTradeId());
        verify(positionService, times(3)).applyTrade(any(), any(), any(), anyInt(), any());
        verify(tradeRepository, times(1)).save(any(Trade.class));
    }

    @Test
    void retryBudgetExhausted_throwsConcurrentTradeException_withCause() {
        when(positionService.applyTrade(any(), any(), any(), anyInt(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Position.class, 1L));

        ConcurrentTradeException ex = assertThrows(ConcurrentTradeException.class,
                () -> tradeService.recordTrade(new TradeRequestDTO(1L, 1L, TradeType.BUY, 1)));

        assertTrue(ex.getMessage().contains("after " + MAX_ATTEMPTS + " attempts"));
        assertInstanceOf(ObjectOptimisticLockingFailureException.class, ex.getCause());
        verify(positionService, times(MAX_ATTEMPTS)).applyTrade(any(), any(), any(), anyInt(), any());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void positionInsertRace_integrityViolation_isRetried() {
        when(positionService.applyTrade(any(), any(), any(), anyInt(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_positions_user_stock"))
                .thenReturn(new Position());

        TradeResponseDTO response = tradeService.recordTrade(new TradeRequestDTO(1L, 1L, TradeType.BUY, 1));

        assertEquals(42L, response.getTradeId());
        verify(positionService, times(2)).applyTrade(any(), any(), any(), anyInt(), any());
    }

    @Test
    void duplicateClientTradeIdRace_resolvesAsReplayOfTheWinner() {
        // first lookup: nothing yet; the insert then collides with the winner; second lookup finds it
        Trade winner = Trade.builder().id(9L).clientTradeId("race-1").build();
        when(tradeRepository.findByClientTradeId("race-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(tradeRepository.save(any(Trade.class)))
                .thenThrow(new DataIntegrityViolationException("uk_trades_client_trade_id"));

        TradeResponseDTO response = tradeService.recordTrade(
                new TradeRequestDTO("race-1", 1L, 1L, TradeType.BUY, 1, null));

        assertEquals(9L, response.getTradeId());
        assertEquals("Trade already recorded", response.getMessage());
        verify(positionService, times(1)).applyTrade(any(), any(), any(), anyInt(), any());
    }

    @Test
    void newClientTradeId_isStoredTrimmed() {
        when(tradeRepository.findByClientTradeId("key-1")).thenReturn(Optional.empty());

        tradeService.recordTrade(new TradeRequestDTO("  key-1 ", 1L, 1L, TradeType.BUY, 2, null));

        assertEquals("key-1", savedTrade().getClientTradeId());
    }

    @Test
    void blankClientTradeId_isTreatedAsAbsent() {
        tradeService.recordTrade(new TradeRequestDTO("   ", 1L, 1L, TradeType.BUY, 1, null));

        verify(tradeRepository, never()).findByClientTradeId(anyString());
        assertNull(savedTrade().getClientTradeId());
    }
}
