package com.demo.upimesh;

import com.demo.upimesh.protocol.*;
import com.demo.upimesh.protocol.exception.InsufficientOfflineBalanceException;
import com.demo.upimesh.protocol.exception.InvalidProtocolVersionException;
import com.demo.upimesh.wallet.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WalletAndProtocolTest {

    @Test
    void testWalletReservationAndCommit() {
        Wallet wallet = new Wallet(WalletId.of("wlet-test"), "alice@demo", new BigDecimal("500.00"));

        assertEquals(0, new BigDecimal("500.00").compareTo(wallet.getAvailableOfflineBalance()));

        wallet.reserveOfflineFunds(new BigDecimal("200.00"));
        assertEquals(0, new BigDecimal("300.00").compareTo(wallet.getAvailableOfflineBalance()));
        assertEquals(0, new BigDecimal("200.00").compareTo(wallet.getReservedOfflineBalance()));

        wallet.commitReservation(new BigDecimal("200.00"));
        assertEquals(0, new BigDecimal("300.00").compareTo(wallet.getPreFundedBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getReservedOfflineBalance()));
    }

    @Test
    void testInsufficientOfflineBalanceThrowsException() {
        Wallet wallet = new Wallet(WalletId.of("wlet-test"), "alice@demo", new BigDecimal("100.00"));

        assertThrows(InsufficientOfflineBalanceException.class, () -> {
            wallet.reserveOfflineFunds(new BigDecimal("150.00"));
        });
    }

    @Test
    void testProtocolVersionCompatibility() {
        ProtocolVersion v100 = new ProtocolVersion("1.0.0");
        ProtocolVersion v110 = new ProtocolVersion("1.1.0");
        ProtocolVersion v200 = new ProtocolVersion("2.0.0");

        assertTrue(v100.isCompatible(v110));
        assertFalse(v100.isCompatible(v200));
    }

    @Test
    void testValueObjectsAndStateEnums() {
        TransactionId txId = TransactionId.generate();
        assertNotNull(txId.value());
        assertTrue(txId.value().startsWith("tx-"));

        WalletId wId = WalletId.of("w1");
        assertEquals("w1", wId.value());

        DeviceId dId = DeviceId.of("d1");
        assertEquals("d1", dId.value());

        KeyId kId = KeyId.of("k1");
        assertEquals("k1", kId.value());

        PacketId pId = PacketId.generate();
        assertNotNull(pId.value());

        assertEquals(TransactionState.SETTLED, TransactionState.valueOf("SETTLED"));
        assertEquals(WalletState.ACTIVE, WalletState.valueOf("ACTIVE"));
    }
}
