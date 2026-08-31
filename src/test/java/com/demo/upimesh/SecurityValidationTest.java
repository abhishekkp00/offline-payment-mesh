package com.demo.upimesh;

import com.demo.upimesh.crypto.Ed25519CryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.WalletAuthorization;
import com.demo.upimesh.protocol.exception.InvalidProtocolVersionException;
import com.demo.upimesh.protocol.exception.StalePacketException;
import com.demo.upimesh.security.SecurityValidationService;
import com.demo.upimesh.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SecurityValidationTest {

    @Autowired private SecurityValidationService securityValidation;
    @Autowired private WalletService walletService;
    @Autowired private Ed25519CryptoService ed25519Service;

    @Test
    void validInstructionPassesValidation() {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        PaymentInstruction instruction = walletService.prepareOfflineInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234");

        assertDoesNotThrow(() -> securityValidation.validateInstruction(instruction));
    }

    @Test
    void expiredTimestampThrowsStalePacketException() {
        WalletAuthorization auth = walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        long oldTimestamp = Instant.now().minusSeconds(100000).toEpochMilli();
        PaymentInstruction instruction = new PaymentInstruction(
                "1.0.0", "tx-2", "alice@demo", "bob@demo",
                new BigDecimal("50.00"), "1234", "nonce-stale", oldTimestamp);
        instruction.setWalletAuthorization(auth);

        KeyPair deviceKeyPair = walletService.getWalletByVpa("alice@demo").getDeviceKeyPair();
        String deviceSig = ed25519Service.sign(instruction.getCanonicalData(), deviceKeyPair.getPrivate());
        instruction.setDeviceSignature(deviceSig);

        assertThrows(StalePacketException.class, () -> securityValidation.validateInstruction(instruction));
    }

    @Test
    void incompatibleProtocolVersionThrowsException() {
        PaymentInstruction instruction = walletService.prepareOfflineInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234");
        instruction.setVersion("2.0.0");

        assertThrows(InvalidProtocolVersionException.class, () -> securityValidation.validateInstruction(instruction));
    }
}
