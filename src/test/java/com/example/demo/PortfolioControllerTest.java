package com.example.demo;

import com.example.demo.controller.PortfolioController;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioControllerTest {

    @Mock
    private PortfolioService portfolioService;

    @InjectMocks
    private PortfolioController portfolioController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetPortfolio_Success() {
        PortfolioResponseDTO mockResponse = new PortfolioResponseDTO();
        mockResponse.setHoldings(Collections.emptyList());
        mockResponse.setTotalHoldingValue(1000.0);
        mockResponse.setTotalBuyPrice(900.0);
        mockResponse.setTotalPL(100.0);
        mockResponse.setTotalPLPercentage(11.11);

        when(portfolioService.getPortfolio(1L)).thenReturn(mockResponse);

        ResponseEntity<PortfolioResponseDTO> response = portfolioController.getPortfolio(1L);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(mockResponse, response.getBody());
        verify(portfolioService, times(1)).getPortfolio(1L);
    }

    @Test
    void testGetPortfolio_UserNotFound() {
        when(portfolioService.getPortfolio(999L)).thenThrow(new IllegalArgumentException("User not found"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            portfolioController.getPortfolio(999L);
        });

        assertEquals("User not found", exception.getMessage());
        verify(portfolioService, times(1)).getPortfolio(999L);
    }

    @Test
    void testGetPortfolio_EmptyPortfolio() {
        PortfolioResponseDTO emptyResponse = new PortfolioResponseDTO();
        emptyResponse.setHoldings(Collections.emptyList());
        emptyResponse.setTotalHoldingValue(0.0);
        emptyResponse.setTotalBuyPrice(0.0);
        emptyResponse.setTotalPL(0.0);
        emptyResponse.setTotalPLPercentage(0.0);

        when(portfolioService.getPortfolio(2L)).thenReturn(emptyResponse);

        ResponseEntity<PortfolioResponseDTO> response = portfolioController.getPortfolio(2L);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(emptyResponse, response.getBody());
        verify(portfolioService, times(1)).getPortfolio(2L);
    }
}

