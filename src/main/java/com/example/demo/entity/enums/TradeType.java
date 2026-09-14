package com.example.demo.entity.enums;

/** Side of a trade. Persisted as its name; the database enforces the same set via a CHECK constraint. */
public enum TradeType {
    BUY,
    SELL
}
