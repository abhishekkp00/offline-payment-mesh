package com.demo.upimesh.security;

import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.ProtocolVersion;
import com.demo.upimesh.protocol.exception.InvalidProtocolVersionException;
import com.demo.upimesh.protocol.exception.StalePacketException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Validates payload freshness, protocol versioning, and replay bounds.
 */
@Service
public class SecurityValidationService {

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    public void validateInstruction(PaymentInstruction instruction) {
        if (instruction == null) {
            throw new IllegalArgumentException("PaymentInstruction cannot be null");
        }

        // 1. Protocol Version Check
        ProtocolVersion version = new ProtocolVersion(
                instruction.getVersion() == null ? "1.0.0" : instruction.getVersion());
        if (!ProtocolVersion.V1_0.isCompatible(version)) {
            throw new InvalidProtocolVersionException("Incompatible protocol version: " + version);
        }

        // 2. Replay Protection / Freshness Window Check
        if (instruction.getSignedAt() == null) {
            throw new StalePacketException("Missing signedAt timestamp");
        }

        long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;
        if (ageSeconds > maxAgeSeconds) {
            throw new StalePacketException("Packet expired (age: " + ageSeconds + "s)");
        }
        if (ageSeconds < -300) {
            throw new StalePacketException("Future-dated transaction timestamp");
        }
    }
}
