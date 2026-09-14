package com.example.demo.exception;

/** A SELL would exceed the quantity currently held. Mapped to HTTP 409 by {@link GlobalExceptionHandler}. */
public class InsufficientPositionException extends RuntimeException {

    public InsufficientPositionException(Long stockId, long held, int requested) {
        super("Cannot sell " + requested + " of stock " + stockId + ": only " + held + " held");
    }
}
