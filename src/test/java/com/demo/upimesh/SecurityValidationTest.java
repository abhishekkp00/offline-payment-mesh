package com.demo.upimesh;

import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.exception.InvalidProtocolVersionException;
import com.demo.upimesh.protocol.exception.StalePacketException;
import com.demo.upimesh.security.SecurityValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SecurityValidationTest {

    @Autowired private SecurityValidationService securityValidation;

    @Test
    void validInstructionPassesValidation() {
        PaymentInstruction instruction = new PaymentInstruction(
                "1.0.0", "tx-1", "alice@demo", "bob@demo",
                new BigDecimal("50.00"), "1234", "nonce-valid", Instant.now().toEpochMilli());

        assertDoesNotThrow(() -> securityValidation.validateInstruction(instruction));
    }

    @Test
    void expiredTimestampThrowsStalePacketException() {
        long oldTimestamp = Instant.now().minusSeconds(100000).toEpochMilli();
        PaymentInstruction instruction = new PaymentInstruction(
                "1.0.0", "tx-2", "alice@demo", "bob@demo",
                new BigDecimal("50.00"), "1234", "nonce-stale", oldTimestamp);

        assertThrows(StalePacketException.class, () -> securityValidation.validateInstruction(instruction));
    }

    @Test
    void incompatibleProtocolVersionThrowsException() {
        PaymentInstruction instruction = new PaymentInstruction(
                "2.0.0", "tx-3", "alice@demo", "bob@demo",
                new BigDecimal("50.00"), "1234", "nonce-incompat", Instant.now().toEpochMilli());

        assertThrows(InvalidProtocolVersionException.class, () -> securityValidation.validateInstruction(instruction));
    }
}
