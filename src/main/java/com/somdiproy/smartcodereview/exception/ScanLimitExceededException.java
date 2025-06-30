package com.somdiproy.smartcodereview.exception;

public class ScanLimitExceededException extends RuntimeException {
    public ScanLimitExceededException(String message) {
        super(message);
    }
    
    public ScanLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}