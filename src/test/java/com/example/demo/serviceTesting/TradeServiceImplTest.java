package com.example.demo.serviceTesting;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
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
    }

    @Test
    void recordTrade_booksAtCurrentMarketPriceAndReturnsTradeId() {
        TradeRequestDTO request = new TradeRequestDTO(1L, 1L, TradeType.BUY, 10);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> {
            Trade t = inv.getArgument(0);
            t.setId(42L);
            return t;
        });

        TradeResponseDTO response = tradeService.recordTrade(request);

        assertEquals(42L, response.getTradeId());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Trade recorded successfully", response.getMessage());

        ArgumentCaptor<Trade> saved = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(saved.capture());
        assertEquals(TradeType.BUY, saved.getValue().getTradeType());
        assertEquals(10, saved.getValue().getQuantity());
        assertEquals(stock.getClosePrice(), saved.getValue().getPrice(), "fill = current close price");
        assertNull(saved.getValue().getClientTradeId());
        verify(tradeRepository, never()).findByClientTradeId(anyString());
    }

    @Test
    void recordTrade_unknownStock_throws404AndBooksNothing() {
        TradeRequestDTO request = new TradeRequestDTO(1L, 999L, TradeType.BUY, 10);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> tradeService.recordTrade(request));

        assertEquals("Stock 999 not found", ex.getMessage());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void recordTrade_unknownUser_throws404() {
        TradeRequestDTO request = new TradeRequestDTO(77L, 1L, TradeType.SELL, 1);
        when(userAccountRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tradeService.recordTrade(request));
        verify(stockRepository, never()).findById(any());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void recordTrade_replayWithSameClientTradeId_returnsOriginalWithoutBookingAgain() {
        Trade original = Trade.builder().id(7L).clientTradeId("abc-123").build();
        when(tradeRepository.findByClientTradeId("abc-123")).thenReturn(Optional.of(original));
        TradeRequestDTO replay = new TradeRequestDTO("abc-123", 1L, 1L, TradeType.BUY, 10);

        TradeResponseDTO response = tradeService.recordTrade(replay);

        assertEquals(7L, response.getTradeId());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Trade already recorded", response.getMessage());
        verify(tradeRepository, never()).save(any());
        verifyNoInteractions(userAccountRepository, stockRepository);
    }

    @Test
    void recordTrade_newClientTradeId_isStoredTrimmed() {
        when(tradeRepository.findByClientTradeId("key-1")).thenReturn(Optional.empty());
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> inv.getArgument(0));

        tradeService.recordTrade(new TradeRequestDTO("  key-1 ", 1L, 1L, TradeType.SELL, 2));

        ArgumentCaptor<Trade> saved = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(saved.capture());
        assertEquals("key-1", saved.getValue().getClientTradeId());
    }

    @Test
    void recordTrade_blankClientTradeId_isTreatedAsAbsent() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> inv.getArgument(0));

        tradeService.recordTrade(new TradeRequestDTO("   ", 1L, 1L, TradeType.BUY, 1));

        verify(tradeRepository, never()).findByClientTradeId(anyString());
    }
}
