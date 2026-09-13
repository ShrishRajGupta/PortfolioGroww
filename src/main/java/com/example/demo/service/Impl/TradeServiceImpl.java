package com.example.demo.service.Impl;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.exception.ConcurrentTradeException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.PositionService;
import com.example.demo.service.TradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Books trades. Each attempt is one transaction that appends the trade to the ledger and applies it
 * to the materialized position; the position row's optimistic lock makes concurrent trades on the
 * same position collide, and the loser retries with a short backoff.
 * <p>
 * Retried: transient failures (optimistic-lock, deadlock, lock timeout) and the integrity violation
 * two first-ever trades raise when both try to create the same position row. A duplicate
 * {@code clientTradeId} that loses the race is resolved as an idempotent replay of the winner.
 * <p>
 * Trade-off: optimistic locking costs nothing without contention and never holds row locks across
 * the request; {@code SELECT ... FOR UPDATE} would serialize every trade on a position instead.
 */
@Service
@Slf4j
public class TradeServiceImpl implements TradeService {

    private final TradeRepository tradeRepository;
    private final StockRepository stockRepository;
    private final UserAccountRepository userAccountRepository;
    private final PositionService positionService;
    private final TransactionOperations transaction;
    private final int maxAttempts;
    private final long retryBackoffMs;

    public TradeServiceImpl(TradeRepository tradeRepository,
                            StockRepository stockRepository,
                            UserAccountRepository userAccountRepository,
                            PositionService positionService,
                            TransactionOperations transaction,
                            @Value("${app.trade.max-attempts:5}") int maxAttempts,
                            @Value("${app.trade.retry-backoff-ms:20}") long retryBackoffMs) {
        this.tradeRepository = tradeRepository;
        this.stockRepository = stockRepository;
        this.userAccountRepository = userAccountRepository;
        this.positionService = positionService;
        this.transaction = transaction;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    @Override
    public TradeResponseDTO recordTrade(TradeRequestDTO request) {
        String clientTradeId = normalizeKey(request.getClientTradeId());

        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> book(request, clientTradeId));
            } catch (TransientDataAccessException | DataIntegrityViolationException e) {
                if (clientTradeId != null) {
                    Optional<Trade> winner = tradeRepository.findByClientTradeId(clientTradeId);
                    if (winner.isPresent()) {
                        log.info("clientTradeId={} lost a race; replaying trade {}", clientTradeId, winner.get().getId());
                        return replay(winner.get());
                    }
                }
                if (attempt >= maxAttempts) {
                    throw new ConcurrentTradeException(attempt, e);
                }
                log.debug("Attempt {}/{} collided ({}); retrying", attempt, maxAttempts, e.getClass().getSimpleName());
                backoff(attempt);
            }
        }
    }

    /** One transactional attempt: idempotency check, validation, position update, ledger append. */
    private TradeResponseDTO book(TradeRequestDTO request, String clientTradeId) {
        if (clientTradeId != null) {
            Optional<Trade> existing = tradeRepository.findByClientTradeId(clientTradeId);
            if (existing.isPresent()) {
                log.info("Idempotent replay of clientTradeId={} -> trade {}", clientTradeId, existing.get().getId());
                return replay(existing.get());
            }
        }

        UserAccount userAccount = userAccountRepository.findById(request.getUserAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("User account", request.getUserAccountId()));
        Stock stock = stockRepository.findById(request.getStockId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock", request.getStockId()));

        BigDecimal fill = request.getExecutionPrice() != null ? request.getExecutionPrice() : stock.getClosePrice();

        positionService.applyTrade(userAccount, stock, request.getTradeType(), request.getQuantity(), fill);

        Trade saved = tradeRepository.save(Trade.builder()
                .clientTradeId(clientTradeId)
                .userAccount(userAccount)
                .stock(stock)
                .tradeType(request.getTradeType())
                .quantity(request.getQuantity())
                .price(fill)
                .build());
        return new TradeResponseDTO(saved.getId(), "SUCCESS", "Trade recorded successfully");
    }

    private static TradeResponseDTO replay(Trade original) {
        return new TradeResponseDTO(original.getId(), "SUCCESS", "Trade already recorded");
    }

    private void backoff(int attempt) {
        if (retryBackoffMs == 0) {
            return;
        }
        long jitter = ThreadLocalRandom.current().nextLong(retryBackoffMs + 1);
        try {
            Thread.sleep(retryBackoffMs * attempt + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ConcurrentTradeException(attempt, ie);
        }
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
