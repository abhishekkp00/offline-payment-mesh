package com.demo.upimesh.protocol;

import java.io.Serializable;
import java.util.UUID;

public record PacketId(String value) implements Serializable {
    public PacketId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PacketId value cannot be blank");
        }
    }

    public static PacketId generate() {
        return new PacketId(UUID.randomUUID().toString());
    }

    public static PacketId of(String id) {
        return new PacketId(id);
    }

    @Override
    public String toString() {
        return value;
    }
}
