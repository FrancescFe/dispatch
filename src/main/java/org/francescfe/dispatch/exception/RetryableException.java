package org.francescfe.dispatch.exception;

public class RetryableException extends RuntimeException {

    public RetryableException(Exception exception) {
        super(exception);
    }
}
