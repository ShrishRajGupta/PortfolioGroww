package com.example.demo.service.Impl;

import com.example.demo.dto.PortfolioHoldingDTO;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TradeRepository tradeRepository;
    private final StockRepository stockRepository;

    @Override
    public PortfolioResponseDTO getPortfolio(Long userId) {
        List<Trade> trades = tradeRepository.findByUserAccountId(userId);

        List<PortfolioHoldingDTO> holdings = new ArrayList<>();
        BigDecimal totalHoldingValue = BigDecimal.ZERO;
        BigDecimal totalBuyPrice = BigDecimal.ZERO;

        for (Trade trade : trades) {
            Optional<Stock> stockOpt = stockRepository.findById(trade.getStock().getId());
            if (stockOpt.isEmpty()) {
                continue;
            }
            Stock stock = stockOpt.get();
            BigDecimal quantity = BigDecimal.valueOf(trade.getQuantity());

            PortfolioHoldingDTO holding = new PortfolioHoldingDTO();
            holding.setStockName(stock.getName());
            holding.setStockId(stock.getId());
            holding.setQuantity(trade.getQuantity());
            holding.setBuyPrice(trade.getPrice());
            holding.setCurrentPrice(stock.getClosePrice());
            holding.setGainLoss(stock.getClosePrice().subtract(trade.getPrice()).multiply(quantity));
            holdings.add(holding);

            totalHoldingValue = totalHoldingValue.add(stock.getClosePrice().multiply(quantity));
            totalBuyPrice = totalBuyPrice.add(trade.getPrice().multiply(quantity));
        }

        BigDecimal totalPL = totalHoldingValue.subtract(totalBuyPrice);
        BigDecimal totalPLPercentage = totalBuyPrice.signum() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalPL.multiply(HUNDRED).divide(totalBuyPrice, 2, RoundingMode.HALF_UP);

        PortfolioResponseDTO response = new PortfolioResponseDTO();
        response.setHoldings(holdings);
        response.setTotalHoldingValue(totalHoldingValue);
        response.setTotalBuyPrice(totalBuyPrice);
        response.setTotalPL(totalPL);
        response.setTotalPLPercentage(totalPLPercentage);
        return response;
    }
}
