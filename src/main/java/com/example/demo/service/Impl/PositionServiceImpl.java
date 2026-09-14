package com.example.demo.service.Impl;

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
import com.example.demo.service.PositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final UserAccountRepository userAccountRepository;

    @Override
    public Position applyTrade(UserAccount user, Stock stock, TradeType type, int quantity, BigDecimal fillPrice) {
        Position position = positionRepository.findByUserAccountIdAndStockId(user.getId(), stock.getId())
                .orElseGet(() -> Position.builder()
                        .userAccount(user)
                        .stock(stock)
                        .netQuantity(0)
                        .avgCost(BigDecimal.ZERO)
                        .realizedPnl(BigDecimal.ZERO)
                        .build());

        if (type == TradeType.SELL && quantity > position.getNetQuantity()) {
            throw new InsufficientPositionException(stock.getId(), position.getNetQuantity(), quantity);
        }

        AverageCostPosition book = new AverageCostPosition(
                position.getNetQuantity(), position.getAvgCost(), position.getRealizedPnl());
        if (type == TradeType.BUY) {
            book.applyBuy(quantity, fillPrice);
        } else {
            book.applySell(quantity, fillPrice);
        }
        position.setNetQuantity(book.getNetQuantity());
        position.setAvgCost(book.getAvgCost());
        position.setRealizedPnl(book.getRealizedPnl());
        return positionRepository.save(position);
    }

    @Override
    @Transactional
    public RebuildResult rebuild(Long userId) {
        Map<Long, AverageCostPosition> book = replayLedger(userId);
        positionRepository.deleteByUserAccountId(userId);
        positionRepository.flush();

        List<Position> fresh = new ArrayList<>();
        UserAccount user = null;
        Map<Long, Stock> stocks = new LinkedHashMap<>();
        for (Trade trade : tradeRepository.findAllForPortfolio(userId)) {
            user = trade.getUserAccount();
            stocks.putIfAbsent(trade.getStock().getId(), trade.getStock());
        }
        for (Map.Entry<Long, AverageCostPosition> entry : book.entrySet()) {
            AverageCostPosition p = entry.getValue();
            fresh.add(Position.builder()
                    .userAccount(user)
                    .stock(stocks.get(entry.getKey()))
                    .netQuantity(p.getNetQuantity())
                    .avgCost(p.getAvgCost())
                    .realizedPnl(p.getRealizedPnl())
                    .build());
        }
        positionRepository.saveAll(fresh);
        log.info("Rebuilt {} positions for user {} from the ledger", fresh.size(), userId);
        return new RebuildResult(1, fresh.size());
    }

    @Override
    @Transactional
    public RebuildResult rebuildAll() {
        int users = 0;
        int positions = 0;
        for (UserAccount user : userAccountRepository.findAll()) {
            positions += rebuild(user.getId()).positionsWritten();
            users++;
        }
        return new RebuildResult(users, positions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PositionDrift> drift(Long userId) {
        Map<Long, AverageCostPosition> ledger = replayLedger(userId);
        Map<Long, Position> stored = positionRepository.findAllForUser(userId).stream()
                .collect(Collectors.toMap(p -> p.getStock().getId(), Function.identity()));

        List<PositionDrift> drifts = new ArrayList<>();
        for (Map.Entry<Long, AverageCostPosition> entry : ledger.entrySet()) {
            AverageCostPosition expected = entry.getValue();
            Position actual = stored.remove(entry.getKey());
            boolean same = actual != null
                    && actual.getNetQuantity() == expected.getNetQuantity()
                    && actual.getAvgCost().compareTo(expected.getAvgCost()) == 0
                    && actual.getRealizedPnl().compareTo(expected.getRealizedPnl()) == 0;
            if (!same) {
                drifts.add(new PositionDrift(entry.getKey(), expected.getNetQuantity(),
                        actual == null ? null : actual.getNetQuantity(),
                        expected.getAvgCost(), actual == null ? null : actual.getAvgCost()));
            }
        }
        // Positions with no trades behind them at all.
        for (Position orphan : stored.values()) {
            drifts.add(new PositionDrift(orphan.getStock().getId(), 0, orphan.getNetQuantity(),
                    BigDecimal.ZERO, orphan.getAvgCost()));
        }
        return drifts;
    }

    /** Replays a user's trades in booking order into one book per stock (first-seen order). */
    private Map<Long, AverageCostPosition> replayLedger(Long userId) {
        Map<Long, AverageCostPosition> book = new LinkedHashMap<>();
        for (Trade trade : tradeRepository.findAllForPortfolio(userId)) {
            AverageCostPosition position = book.computeIfAbsent(trade.getStock().getId(), id -> new AverageCostPosition());
            if (trade.getTradeType() == TradeType.BUY) {
                position.applyBuy(trade.getQuantity(), trade.getPrice());
            } else {
                position.applySell(trade.getQuantity(), trade.getPrice());
            }
        }
        return book;
    }
}
