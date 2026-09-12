package com.example.demo.web;

import com.example.demo.controller.PortfolioController;
import com.example.demo.dto.PortfolioHoldingDTO;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.exception.InsufficientPositionException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.service.PortfolioService;
import com.example.demo.service.StockService;
import com.example.demo.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of /api: request validation, response shapes and the RFC 7807 error shape produced
 * by GlobalExceptionHandler. Services are mocked; this is a web slice, no database.
 */
@WebMvcTest(PortfolioController.class)
class PortfolioControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TradeService tradeService;

    @MockBean
    private PortfolioService portfolioService;

    @MockBean
    private StockService stockService;

    private static final String VALID_TRADE = """
            {"clientTradeId":"11111111-2222-3333-4444-555555555555",
             "userAccountId":1,"stockId":2,"tradeType":"BUY","quantity":3}
            """;

    @Test
    void recordTrade_validBody_returnsBookedTrade() throws Exception {
        when(tradeService.recordTrade(any())).thenReturn(new TradeResponseDTO(9L, "SUCCESS", "Trade recorded successfully"));

        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON).content(VALID_TRADE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(9))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void recordTrade_withExecutionPrice_isAccepted() throws Exception {
        when(tradeService.recordTrade(any())).thenReturn(new TradeResponseDTO(10L, "SUCCESS", "Trade recorded successfully"));

        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAccountId\":1,\"stockId\":2,\"tradeType\":\"SELL\",\"quantity\":1,\"executionPrice\":101.2345}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(10));
    }

    @Test
    void recordTrade_missingFields_returns400ProblemWithFieldErrors() throws Exception {
        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tradeType\":\"BUY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.userAccountId").exists())
                .andExpect(jsonPath("$.errors.stockId").exists())
                .andExpect(jsonPath("$.errors.quantity").exists());

        verify(tradeService, never()).recordTrade(any());
    }

    @Test
    void recordTrade_nonPositiveQuantity_returns400() throws Exception {
        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAccountId\":1,\"stockId\":2,\"tradeType\":\"SELL\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value(containsString("greater than 0")));

        verify(tradeService, never()).recordTrade(any());
    }

    @Test
    void recordTrade_badExecutionPrice_returns400() throws Exception {
        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAccountId\":1,\"stockId\":2,\"tradeType\":\"BUY\",\"quantity\":1,\"executionPrice\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.executionPrice").value(containsString("greater than 0")));

        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAccountId\":1,\"stockId\":2,\"tradeType\":\"BUY\",\"quantity\":1,\"executionPrice\":1.123456}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.executionPrice").value(containsString("4 fraction")));

        verify(tradeService, never()).recordTrade(any());
    }

    @Test
    void recordTrade_unknownTradeType_returns400Malformed() throws Exception {
        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAccountId\":1,\"stockId\":2,\"tradeType\":\"HOLD\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.detail").value(containsString("HOLD")));

        verify(tradeService, never()).recordTrade(any());
    }

    @Test
    void recordTrade_unknownUser_returns404Problem() throws Exception {
        when(tradeService.recordTrade(any())).thenThrow(new ResourceNotFoundException("User account", 99L));

        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON).content(VALID_TRADE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User account 99 not found"));
    }

    @Test
    void recordTrade_oversell_returns409InsufficientPosition() throws Exception {
        when(tradeService.recordTrade(any())).thenThrow(new InsufficientPositionException(2L, 4, 5));

        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON).content(VALID_TRADE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient position"))
                .andExpect(jsonPath("$.detail").value("Cannot sell 5 of stock 2: only 4 held"));
    }

    @Test
    void recordTrade_concurrentDuplicateKey_returns409() throws Exception {
        when(tradeService.recordTrade(any())).thenThrow(new DataIntegrityViolationException("uk_trades_client_trade_id"));

        mvc.perform(post("/api/trade").contentType(MediaType.APPLICATION_JSON).content(VALID_TRADE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void getPortfolio_serializesHoldingsAndTotals() throws Exception {
        PortfolioHoldingDTO holding = PortfolioHoldingDTO.builder()
                .stockId(1L).stockName("ACME").netQuantity(6)
                .avgCost(new BigDecimal("100.0000")).marketPrice(new BigDecimal("120.0000"))
                .costBasis(new BigDecimal("600.0000")).marketValue(new BigDecimal("720.0000"))
                .unrealizedPnl(new BigDecimal("120.0000")).realizedPnl(new BigDecimal("120.0000"))
                .build();
        when(portfolioService.getPortfolio(1L)).thenReturn(PortfolioResponseDTO.builder()
                .holdings(List.of(holding))
                .totalMarketValue(new BigDecimal("720.0000")).totalCostBasis(new BigDecimal("600.0000"))
                .totalUnrealizedPnl(new BigDecimal("120.0000")).totalRealizedPnl(new BigDecimal("120.0000"))
                .totalPnl(new BigDecimal("240.0000")).unrealizedReturnPercentage(new BigDecimal("20.00"))
                .build());

        mvc.perform(get("/api/portfolio/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[0].stockName").value("ACME"))
                .andExpect(jsonPath("$.holdings[0].netQuantity").value(6))
                .andExpect(jsonPath("$.holdings[0].avgCost").value(100.0))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnl").value(120.0))
                .andExpect(jsonPath("$.totalPnl").value(240.0))
                .andExpect(jsonPath("$.unrealizedReturnPercentage").value(20.0));
    }

    @Test
    void getStockById_missing_returns404Problem() throws Exception {
        when(stockService.findStockById(404L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/stocks/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Stock 404 not found"));
    }

    @Test
    void getStockById_present_returnsDecimalPricesAsNumbers() throws Exception {
        when(stockService.findStockById(1L)).thenReturn(Optional.of(new Stock(1L, "Stock1",
                new BigDecimal("100.0000"), new BigDecimal("105.5000"), new BigDecimal("110.0000"),
                new BigDecimal("95.0000"), new BigDecimal("102.5000"))));

        mvc.perform(get("/api/stocks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stock1"))
                .andExpect(jsonPath("$.closePrice").value(105.5));
    }

    @Test
    void getStockById_nonNumericId_returns400() throws Exception {
        mvc.perform(get("/api/stocks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void searchStock_missingParam_returns400Problem() throws Exception {
        mvc.perform(get("/api/stocks/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }
}
