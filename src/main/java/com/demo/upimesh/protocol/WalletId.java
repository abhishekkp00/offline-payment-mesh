package com.demo.upimesh.protocol;

import java.io.Serializable;

public record WalletId(String value) implements Serializable {
    public WalletId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("WalletId value cannot be blank");
        }
    }

    public static WalletId of(String id) {
        return new WalletId(id);
    }

    @Override
    public String toString() {
        return value;
    }
}
