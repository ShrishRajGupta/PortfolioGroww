package com.example.demo.service.Impl;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * <p>
     * Idempotency: when the request carries a {@code clientTradeId} that was already booked, the
     * original trade is returned instead of a second booking. A genuinely concurrent duplicate
     * (two requests racing past the lookup) is stopped by the unique index and surfaces as a
     * 409 Conflict via the global handler — the first caller's booking stands.
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

        Trade trade = Trade.builder()
                .clientTradeId(clientTradeId)
                .userAccount(userAccount)
                .stock(stock)
                .tradeType(request.getTradeType())
                .quantity(request.getQuantity())
                .price(stock.getClosePrice())   // filled at current market price
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
