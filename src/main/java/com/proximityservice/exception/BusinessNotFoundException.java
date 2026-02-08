package com.proximityservice.exception;

public class BusinessNotFoundException extends RuntimeException {

    public BusinessNotFoundException(String businessId) {
        super("Business not found: " + businessId);
    }
}
