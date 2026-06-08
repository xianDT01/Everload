package com.EverLoad.everload.service;

/** Raised when an InnerTube HTTP call fails (network, non-2xx, malformed JSON). */
public class YtMusicTransportException extends RuntimeException {
    public YtMusicTransportException(String message) {
        super(message);
    }

    public YtMusicTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
