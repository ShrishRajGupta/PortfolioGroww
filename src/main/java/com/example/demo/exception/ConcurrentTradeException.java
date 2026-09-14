package com.example.demo.exception;

/**
 * The trade kept colliding with concurrent updates to the same position and the retry budget ran
 * out. Nothing was booked; the client can simply retry. Mapped to HTTP 409.
 */
public class ConcurrentTradeException extends RuntimeException {

    public ConcurrentTradeException(int attempts, Throwable cause) {
        super("Trade could not be booked after " + attempts + " attempts because of concurrent updates; please retry", cause);
    }
}
