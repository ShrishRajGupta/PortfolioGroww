package com.example.demo.dto;

/** Outcome of rebuilding materialized positions from the ledger. */
public record RebuildResult(int usersRebuilt, int positionsWritten) {
}
