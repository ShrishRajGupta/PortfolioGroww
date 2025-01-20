package com.example.demo.serviceTesting;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.Impl.TradeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRecordTrade_Success() {
        // Mock input
        TradeRequestDTO tradeRequest = new TradeRequestDTO(1L, 1L, "BUY", 10);

        // Mock user and stock
        UserAccount userAccount = new UserAccount(1L, "User1", "user1@example.com", null);
        Stock stock = new Stock(1L, "Stock1", 100.0, 105.0, 110.0, 95.0, 102.5);

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(userAccount));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        TradeResponseDTO response = tradeService.recordTrade(tradeRequest);

        // Verify
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Trade recorded successfully", response.getMessage());
        verify(userAccountRepository, times(1)).findById(1L);
        verify(stockRepository, times(1)).findById(1L);
        verify(tradeRepository, times(1)).save(any(Trade.class));
    }

    @Test
    void testRecordTrade_InvalidStock() {
        // Mock input
        TradeRequestDTO tradeRequest = new TradeRequestDTO(1L, 999L, "BUY", 10);

        // Mock user and stock
        UserAccount userAccount = new UserAccount(1L, "User1", "user1@example.com", null);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(userAccount));
        when(stockRepository.findById(999L)).thenReturn(Optional.empty());

        // Execute
        TradeResponseDTO response = tradeService.recordTrade(tradeRequest);

        // Verify
        assertNotNull(response);
        assertEquals("FAILURE", response.getStatus());
        assertEquals("Invalid user or stock ID", response.getMessage());
        verify(userAccountRepository, times(1)).findById(1L);
        verify(stockRepository, times(1)).findById(999L);
        verify(tradeRepository, never()).save(any(Trade.class));
    }
}
