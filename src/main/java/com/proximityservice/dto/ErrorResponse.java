package com.proximityservice.dto;

import java.util.Map;

public record ErrorResponse(
        String error,
        String message,
        Map<String, Object> details
) {
    public ErrorResponse(String error, String message) {
        this(error, message, null);
    }
}
