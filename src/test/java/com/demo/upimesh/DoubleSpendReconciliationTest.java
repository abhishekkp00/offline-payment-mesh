package com.demo.upimesh;

import com.demo.upimesh.bridge.BridgeIngestionService;
import com.demo.upimesh.crypto.Ed25519CryptoService;
import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.idempotency.IdempotencyService;
import com.demo.upimesh.persistence.AccountRepository;
import com.demo.upimesh.persistence.TransactionRepository;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.WalletAuthorization;
import com.demo.upimesh.reconciliation.DoubleSpendReconciliationService;
import com.demo.upimesh.simulator.DemoService;
import com.demo.upimesh.wallet.Wallet;
import com.demo.upimesh.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DoubleSpendReconciliationTest {

    @Autowired private DemoService demoService;
    @Autowired private WalletService walletService;
    @Autowired private Ed25519CryptoService ed25519Service;
    @Autowired private ServerKeyHolder serverKey;
    @Autowired private HybridCryptoService crypto;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private IdempotencyService idempotency;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private DoubleSpendReconciliationService reconciliationService;

    @BeforeEach
    void clear() {
        idempotency.clear();
        reconciliationService.clear();
    }

    // 1. Single valid offline spend within limit -> SETTLED
    @Test
    void testSingleValidOfflineSpend() throws Exception {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", new BigDecimal("100.00"), "1234", 5);

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("SETTLED", result.outcome());
    }

    // 2. Multiple valid offline spends whose sum <= authorized limit -> BOTH SETTLED
    @Test
    void testMultipleValidSpendsWithinAllowance() throws Exception {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);

        MeshPacket packet1 = demoService.createPacket("alice@demo", "bob@demo", new BigDecimal("200.00"), "1234", 5);
        BridgeIngestionService.IngestResult r1 = bridge.ingest(packet1, "bridge-1", 1);
        assertEquals("SETTLED", r1.outcome());

        MeshPacket packet2 = demoService.createPacket("alice@demo", "carol@demo", new BigDecimal("250.00"), "1234", 5);
        BridgeIngestionService.IngestResult r2 = bridge.ingest(packet2, "bridge-2", 1);
        assertEquals("SETTLED", r2.outcome());
    }

    // 3. Double-Spend Attack across two isolated merchants -> First SETTLED, Second CONFLICTED
    @Test
    void testOfflineDoubleSpendAcrossIsolatedMerchants() throws Exception {
        // Alice obtains ₹500 offline allowance
        WalletAuthorization auth = walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        Wallet wallet = walletService.getWalletByVpa("alice@demo");

        // Alice creates Payment A (₹350) for Merchant Bob
        PaymentInstruction paymentA = new PaymentInstruction(
                "alice@demo", "bob@demo", new BigDecimal("350.00"), "1234", "nonce-A-" + UUID.randomUUID(), System.currentTimeMillis());
        paymentA.setWalletAuthorization(auth);
        String sigA = ed25519Service.sign(paymentA.getCanonicalData(), wallet.getDeviceKeyPair().getPrivate());
        paymentA.setDeviceSignature(sigA);

        MeshPacket packetA = new MeshPacket();
        packetA.setPacketId(UUID.randomUUID().toString());
        packetA.setTransactionId(paymentA.getTransactionId());
        packetA.setWalletId(auth.getWalletId());
        packetA.setCreatedAt(paymentA.getSignedAt());
        packetA.setCiphertext(crypto.encrypt(paymentA, serverKey.getPublicKey(), packetA.getCanonicalAad()));

        // Alice creates Payment B (₹350) for Merchant Carol using the SAME ₹500 allowance (DOUBLE SPEND)
        PaymentInstruction paymentB = new PaymentInstruction(
                "alice@demo", "carol@demo", new BigDecimal("350.00"), "1234", "nonce-B-" + UUID.randomUUID(), System.currentTimeMillis());
        paymentB.setWalletAuthorization(auth);
        String sigB = ed25519Service.sign(paymentB.getCanonicalData(), wallet.getDeviceKeyPair().getPrivate());
        paymentB.setDeviceSignature(sigB);

        MeshPacket packetB = new MeshPacket();
        packetB.setPacketId(UUID.randomUUID().toString());
        packetB.setTransactionId(paymentB.getTransactionId());
        packetB.setWalletId(auth.getWalletId());
        packetB.setCreatedAt(paymentB.getSignedAt());
        packetB.setCiphertext(crypto.encrypt(paymentB, serverKey.getPublicKey(), packetB.getCanonicalAad()));

        // Merchant Bob uploads Packet A first -> SETTLED
        BridgeIngestionService.IngestResult resultA = bridge.ingest(packetA, "bridge-bob", 1);
        assertEquals("SETTLED", resultA.outcome());

        // Merchant Carol uploads Packet B second -> CONFLICTED (Double Spend Detected)
        BridgeIngestionService.IngestResult resultB = bridge.ingest(packetB, "bridge-carol", 1);
        assertEquals("INVALID", resultB.outcome());
        assertTrue(resultB.reason().contains("CONFLICTED"));
    }

    // 4. Overspending single transaction -> REJECTED (exceeds authorized balance)
    @Test
    void testOverspendingSingleTransaction() throws Exception {
        WalletAuthorization auth = walletService.issueWalletAuthorization("alice@demo", new BigDecimal("200.00"), 3600);
        Wallet wallet = walletService.getWalletByVpa("alice@demo");

        PaymentInstruction overspendTx = new PaymentInstruction(
                "alice@demo", "bob@demo", new BigDecimal("300.00"), "1234", UUID.randomUUID().toString(), System.currentTimeMillis());
        overspendTx.setWalletAuthorization(auth);
        String sig = ed25519Service.sign(overspendTx.getCanonicalData(), wallet.getDeviceKeyPair().getPrivate());
        overspendTx.setDeviceSignature(sig);

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTransactionId(overspendTx.getTransactionId());
        packet.setWalletId(auth.getWalletId());
        packet.setCreatedAt(overspendTx.getSignedAt());
        packet.setCiphertext(crypto.encrypt(overspendTx, serverKey.getPublicKey(), packet.getCanonicalAad()));

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("exceeds authorized offline allowance") || result.reason().contains("OVERSPENT"));
    }

    // 5. Replay Attack -> REPLAY_ATTACK
    @Test
    void testReplayAttackDetected() throws Exception {
        WalletAuthorization auth = walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        Wallet wallet = walletService.getWalletByVpa("alice@demo");

        String reusedNonce = "fixed-nonce-12345";
        PaymentInstruction tx1 = new PaymentInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", reusedNonce, System.currentTimeMillis());
        tx1.setWalletAuthorization(auth);
        tx1.setDeviceSignature(ed25519Service.sign(tx1.getCanonicalData(), wallet.getDeviceKeyPair().getPrivate()));

        MeshPacket p1 = new MeshPacket();
        p1.setPacketId(UUID.randomUUID().toString());
        p1.setTransactionId(tx1.getTransactionId());
        p1.setWalletId(auth.getWalletId());
        p1.setCreatedAt(tx1.getSignedAt());
        p1.setCiphertext(crypto.encrypt(tx1, serverKey.getPublicKey(), p1.getCanonicalAad()));

        BridgeIngestionService.IngestResult r1 = bridge.ingest(p1, "bridge-1", 1);
        assertEquals("SETTLED", r1.outcome());

        // Replay with different txId but SAME nonce
        PaymentInstruction tx2 = new PaymentInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", reusedNonce, System.currentTimeMillis());
        tx2.setWalletAuthorization(auth);
        tx2.setDeviceSignature(ed25519Service.sign(tx2.getCanonicalData(), wallet.getDeviceKeyPair().getPrivate()));

        MeshPacket p2 = new MeshPacket();
        p2.setPacketId(UUID.randomUUID().toString());
        p2.setTransactionId(tx2.getTransactionId());
        p2.setWalletId(auth.getWalletId());
        p2.setCreatedAt(tx2.getSignedAt());
        p2.setCiphertext(crypto.encrypt(tx2, serverKey.getPublicKey(), p2.getCanonicalAad()));

        BridgeIngestionService.IngestResult r2 = bridge.ingest(p2, "bridge-2", 1);
        assertEquals("INVALID", r2.outcome());
        assertTrue(r2.reason().contains("REPLAY_ATTACK"));
    }
}
