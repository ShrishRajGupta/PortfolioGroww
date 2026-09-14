package com.example.demo.service;

import com.example.demo.dto.PositionDrift;
import com.example.demo.dto.RebuildResult;
import com.example.demo.entity.Position;
import com.example.demo.entity.Stock;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;

import java.math.BigDecimal;
import java.util.List;

public interface PositionService {

    /**
     * Applies one trade to the user's position in the stock (creating it on the first trade) and
     * returns the updated row. Must run inside the caller's transaction, alongside the ledger insert.
     *
     * @throws com.example.demo.exception.InsufficientPositionException for a SELL beyond the units held
     */
    Position applyTrade(UserAccount user, Stock stock, TradeType type, int quantity, BigDecimal fillPrice);

    /** Rebuilds one user's positions from the ledger. */
    RebuildResult rebuild(Long userId);

    /** Rebuilds every user's positions from the ledger. */
    RebuildResult rebuildAll();

    /** Compares the ledger with the stored positions of one user; empty means no drift. */
    List<PositionDrift> drift(Long userId);
}
