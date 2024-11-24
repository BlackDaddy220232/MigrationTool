package org.example.exception;

public class LockException extends RuntimeException {
    public LockException(String message, Throwable cause) {
        super(message, cause);
    }
}

