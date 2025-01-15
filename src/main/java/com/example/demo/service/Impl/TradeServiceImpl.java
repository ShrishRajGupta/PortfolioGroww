
package com.example.demo.service.Impl;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
        import com.example.demo.repository.*;
import com.example.demo.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    @Autowired
    private final TradeRepository tradeRepository;

    @Autowired
    private final StockRepository stockRepository;

    @Autowired
    private final UserAccountRepository userAccountRepository;

    @Override
    public TradeResponseDTO recordTrade(TradeRequestDTO tradeRequest) {
        Optional<UserAccount> userAccount = userAccountRepository.findById(tradeRequest.getUserAccountId());
        Optional<Stock> stock = stockRepository.findById(tradeRequest.getStockId());

        if (userAccount.isPresent() && stock.isPresent()) {
            Trade trade = new Trade();
            trade.setUserAccount(userAccount.get());
            trade.setStock(stock.get());
            trade.setTradeType(tradeRequest.getTradeType());
            trade.setQuantity(tradeRequest.getQuantity());
            trade.setPrice(stock.get().getClosePrice());
            tradeRepository.save(trade);

            TradeResponseDTO response = new TradeResponseDTO();
            response.setStatus("SUCCESS");
            response.setMessage("Trade recorded successfully");
            return response;
        }

        TradeResponseDTO response = new TradeResponseDTO();
        response.setStatus("FAILURE");
        response.setMessage("Invalid user or stock ID");
        return response;
    }
}