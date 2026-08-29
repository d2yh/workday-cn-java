package com.github.d2yh.workday.exception;

/**
 * Base exception for holiday-cn-java
 */
public class HolidayException extends RuntimeException {
    
    public HolidayException(String message) {
        super(message);
    }
    
    public HolidayException(String message, Throwable cause) {
        super(message, cause);
    }
}
