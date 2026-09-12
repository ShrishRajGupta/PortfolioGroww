package com.example.demo.service.Impl;

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
import com.example.demo.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    private final TradeRepository tradeRepository;
    private final StockRepository stockRepository;
    private final UserAccountRepository userAccountRepository;

    /**
     * Books a trade in one transaction.
     * <ul>
     *   <li>Idempotency: a request whose {@code clientTradeId} was already booked returns the original
     *       trade. A concurrent duplicate is stopped by the unique index and surfaces as 409.</li>
     *   <li>Fill price: {@code executionPrice} when given, otherwise the stock's current close price.</li>
     *   <li>SELL must not exceed the units currently held (409). The check reads the ledger inside the
     *       same transaction; two SELLs racing each other can still both pass on MySQL's default
     *       isolation — closing that gap is what the materialized, versioned position row is for.</li>
     * </ul>
     */
    @Override
    @Transactional
    public TradeResponseDTO recordTrade(TradeRequestDTO request) {
        String clientTradeId = normalizeKey(request.getClientTradeId());

        if (clientTradeId != null) {
            Optional<Trade> existing = tradeRepository.findByClientTradeId(clientTradeId);
            if (existing.isPresent()) {
                log.info("Idempotent replay of clientTradeId={} -> trade {}", clientTradeId, existing.get().getId());
                return new TradeResponseDTO(existing.get().getId(), "SUCCESS", "Trade already recorded");
            }
        }

        UserAccount userAccount = userAccountRepository.findById(request.getUserAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("User account", request.getUserAccountId()));
        Stock stock = stockRepository.findById(request.getStockId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock", request.getStockId()));

        if (request.getTradeType() == TradeType.SELL) {
            long held = tradeRepository.netPosition(userAccount.getId(), stock.getId());
            if (request.getQuantity() > held) {
                throw new InsufficientPositionException(stock.getId(), held, request.getQuantity());
            }
        }

        BigDecimal fill = request.getExecutionPrice() != null ? request.getExecutionPrice() : stock.getClosePrice();

        Trade trade = Trade.builder()
                .clientTradeId(clientTradeId)
                .userAccount(userAccount)
                .stock(stock)
                .tradeType(request.getTradeType())
                .quantity(request.getQuantity())
                .price(fill)
                .build();

        Trade saved = tradeRepository.save(trade);
        return new TradeResponseDTO(saved.getId(), "SUCCESS", "Trade recorded successfully");
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
