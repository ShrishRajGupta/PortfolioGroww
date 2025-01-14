package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PortfolioService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private StockRepository stockRepository;

    public PortfolioResponseDTO getPortfolio(Long userId) {
        List<Trade> trades = tradeRepository.findByUserAccountId(userId);

        List<PortfolioHoldingDTO> holdings = new ArrayList<>();
        double totalHoldingValue = 0.0;
        double totalBuyPrice = 0.0;

        for (Trade trade : trades) {
            Optional<Stock> stockOpt = stockRepository.findById(trade.getStock().getId());
            if (stockOpt.isPresent()) {
                Stock stock = stockOpt.get();
                PortfolioHoldingDTO holding = new PortfolioHoldingDTO();
                holding.setStockName(stock.getName());
                holding.setStockId(stock.getId());
                holding.setQuantity(trade.getQuantity());
                holding.setBuyPrice(trade.getPrice());
                holding.setCurrentPrice(stock.getClosePrice());
                double gainLoss = (stock.getClosePrice() - trade.getPrice()) * trade.getQuantity();
                holding.setGainLoss(gainLoss);

                holdings.add(holding);

                totalHoldingValue += stock.getClosePrice() * trade.getQuantity();
                totalBuyPrice += trade.getPrice() * trade.getQuantity();
            }
        }

        double totalPL = totalHoldingValue - totalBuyPrice;
        double totalPLPercentage = (totalPL / totalBuyPrice) * 100;

        PortfolioResponseDTO response = new PortfolioResponseDTO();
        response.setHoldings(holdings);
        response.setTotalHoldingValue(totalHoldingValue);
        response.setTotalBuyPrice(totalBuyPrice);
        response.setTotalPL(totalPL);
        response.setTotalPLPercentage(totalPLPercentage);

        return response;
    }
}
