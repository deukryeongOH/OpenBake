package com.openbake.order.application.port.dto;

public record PaymentResult(String status, String message) {

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
