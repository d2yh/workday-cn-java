package com.github.d2yh.workday.exception;

/**
 * Exception thrown when fetching holiday data fails
 */
public class HolidayFetchException extends HolidayException {
    
    public HolidayFetchException(String message) {
        super(message);
    }
    
    public HolidayFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
