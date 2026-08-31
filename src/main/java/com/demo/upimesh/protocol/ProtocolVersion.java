package com.demo.upimesh.protocol;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record ProtocolVersion(String version) implements Serializable {
    public static final ProtocolVersion V1_0 = new ProtocolVersion("1.0.0");

    public ProtocolVersion {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Protocol version cannot be empty");
        }
    }

    public boolean isCompatible(ProtocolVersion other) {
        if (other == null) return false;
        String[] thisParts = this.version.split("\\.");
        String[] otherParts = other.version.split("\\.");
        return thisParts[0].equals(otherParts[0]); // Major version match
    }

    @Override
    public String toString() {
        return version;
    }
}
