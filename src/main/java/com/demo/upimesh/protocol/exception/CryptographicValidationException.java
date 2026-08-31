package com.demo.upimesh.protocol.exception;

public class CryptographicValidationException extends OfflineMeshException {
    public CryptographicValidationException(String message) {
        super(message);
    }

    public CryptographicValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
