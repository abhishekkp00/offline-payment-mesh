package com.demo.upimesh.protocol.exception;

public class StalePacketException extends OfflineMeshException {
    public StalePacketException(String message) {
        super(message);
    }
}
