package com.example.demo.dto;

import lombok.Data;

@Data
public class TradeResponseDTO {
    private String status;
    private String message;

    public TradeResponseDTO(String success, String tradeRecordedSuccessfully) {
        this.status=success;
        this.message=tradeRecordedSuccessfully;
    }

    public TradeResponseDTO() {

    }
}
