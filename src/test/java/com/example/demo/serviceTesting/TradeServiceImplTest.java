package com.example.demo.serviceTesting;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.exception.InsufficientPositionException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.Impl.TradeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TradeServiceImplTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private TradeServiceImpl tradeService;

    private final UserAccount user = new UserAccount(1L, "User1", "user1@example.com", null);
    private final Stock stock = new Stock(1L, "Stock1",
            new BigDecimal("100.0000"), new BigDecimal("105.5000"), new BigDecimal("110.0000"),
            new BigDecimal("95.0000"), new BigDecimal("102.5000"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
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
    void buy_withoutExecutionPrice_fillsAtCurrentClose() {
        TradeResponseDTO response = tradeService.recordTrade(new TradeRequestDTO(1L, 1L, TradeType.BUY, 10));

        assertEquals(42L, response.getTradeId());
        assertEquals("SUCCESS", response.getStatus());
        Trade t = savedTrade();
        assertEquals(TradeType.BUY, t.getTradeType());
        assertEquals(10, t.getQuantity());
        assertEquals(stock.getClosePrice(), t.getPrice());
        assertNull(t.getClientTradeId());
        verify(tradeRepository, never()).netPosition(anyLong(), anyLong());
    }

    @Test
    void buy_withExecutionPrice_fillsAtThatPrice() {
        TradeRequestDTO request = new TradeRequestDTO(null, 1L, 1L, TradeType.BUY, 3, new BigDecimal("101.2500"));

        tradeService.recordTrade(request);

        assertEquals(new BigDecimal("101.2500"), savedTrade().getPrice());
    }

    @Test
    void sell_withinPosition_isBooked() {
        when(tradeRepository.netPosition(1L, 1L)).thenReturn(10L);

        tradeService.recordTrade(new TradeRequestDTO(1L, 1L, TradeType.SELL, 10));

        assertEquals(TradeType.SELL, savedTrade().getTradeType());
    }

    @Test
    void sell_beyondPosition_is409AndBooksNothing() {
        when(tradeRepository.netPosition(1L, 1L)).thenReturn(4L);

        InsufficientPositionException ex = assertThrows(InsufficientPositionException.class,
                () -> tradeService.recordTrade(new TradeRequestDTO(1L, 1L, TradeType.SELL, 5)));

        assertEquals("Cannot sell 5 of stock 1: only 4 held", ex.getMessage());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void sell_withNoPosition_is409() {
        when(tradeRepository.netPosition(1L, 1L)).thenReturn(0L);

        assertThrows(InsufficientPositionException.class,
                () -> tradeService.recordTrade(new TradeRequestDTO(1L, 1L, TradeType.SELL, 1)));
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void unknownStock_throws404AndBooksNothing() {
        when(stockRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> tradeService.recordTrade(new TradeRequestDTO(1L, 999L, TradeType.BUY, 10)));

        assertEquals("Stock 999 not found", ex.getMessage());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void unknownUser_throws404() {
        when(userAccountRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tradeService.recordTrade(new TradeRequestDTO(77L, 1L, TradeType.SELL, 1)));
        verify(stockRepository, never()).findById(any());
        verify(tradeRepository, never()).save(any());
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
        verifyNoInteractions(userAccountRepository, stockRepository);
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
