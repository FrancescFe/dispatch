package org.francescfe.dispatch.exception;

public class NonRetryableException extends RuntimeException {

    public NonRetryableException(Exception exception) {
        super(exception);
    }
}
