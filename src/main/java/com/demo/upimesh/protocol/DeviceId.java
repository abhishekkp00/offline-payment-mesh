package com.demo.upimesh.protocol;

import java.io.Serializable;

public record DeviceId(String value) implements Serializable {
    public DeviceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DeviceId value cannot be blank");
        }
    }

    public static DeviceId of(String id) {
        return new DeviceId(id);
    }

    @Override
    public String toString() {
        return value;
    }
}
