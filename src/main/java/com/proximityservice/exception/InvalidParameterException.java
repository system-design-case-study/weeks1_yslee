package com.proximityservice.exception;

import java.util.Map;
import lombok.Getter;

@Getter
public class InvalidParameterException extends RuntimeException {

    private final Map<String, Object> details;

    public InvalidParameterException(String message, Map<String, Object> details) {
        super(message);
        this.details = details;
    }

    public InvalidParameterException(String message) {
        this(message, null);
    }
}
