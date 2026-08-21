package com.everload.everload.service;

public class SpotifyServiceException extends RuntimeException {

    public SpotifyServiceException(String message) {
        super(message);
    }

    public SpotifyServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
