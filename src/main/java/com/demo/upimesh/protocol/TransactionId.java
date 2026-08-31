package com.demo.upimesh.protocol;

import java.io.Serializable;
import java.util.UUID;

public record TransactionId(String value) implements Serializable {
    public TransactionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TransactionId value cannot be blank");
        }
    }

    public static TransactionId generate() {
        return new TransactionId("tx-" + UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
