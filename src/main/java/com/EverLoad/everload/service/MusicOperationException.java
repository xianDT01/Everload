package com.everload.everload.service;

public class MusicOperationException extends RuntimeException {

    public MusicOperationException(String message) {
        super(message);
    }

    public MusicOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
