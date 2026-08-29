package com.github.d2yh.workday.exception;

/**
 * Exception thrown when parsing holiday data fails
 */
public class HolidayParseException extends HolidayException {
    
    public HolidayParseException(String message) {
        super(message);
    }
    
    public HolidayParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
