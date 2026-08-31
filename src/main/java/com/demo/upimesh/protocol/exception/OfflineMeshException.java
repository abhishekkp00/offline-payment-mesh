package com.demo.upimesh.protocol.exception;

public class OfflineMeshException extends RuntimeException {
    public OfflineMeshException(String message) {
        super(message);
    }

    public OfflineMeshException(String message, Throwable cause) {
        super(message, cause);
    }
}
