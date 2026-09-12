package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeResponseDTO {
    /** Id of the booked trade; identical on an idempotent replay. */
    private Long tradeId;
    private String status;
    private String message;

    public TradeResponseDTO(String status, String message) {
        this(null, status, message);
    }
}
