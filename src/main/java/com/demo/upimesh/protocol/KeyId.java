package com.demo.upimesh.protocol;

import java.io.Serializable;

public record KeyId(String value) implements Serializable {
    public KeyId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("KeyId value cannot be blank");
        }
    }

    public static KeyId of(String id) {
        return new KeyId(id);
    }

    @Override
    public String toString() {
        return value;
    }
}
