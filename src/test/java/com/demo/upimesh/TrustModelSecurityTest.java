package com.demo.upimesh;

import com.demo.upimesh.bridge.BridgeIngestionService;
import com.demo.upimesh.crypto.Ed25519CryptoService;
import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.KeyLifecycleService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.idempotency.IdempotencyService;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.WalletAuthorization;
import com.demo.upimesh.security.BridgeTrustService;
import com.demo.upimesh.security.DeviceTrustService;
import com.demo.upimesh.simulator.DemoService;
import com.demo.upimesh.wallet.Wallet;
import com.demo.upimesh.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TrustModelSecurityTest {

    @Autowired private DemoService demoService;
    @Autowired private WalletService walletService;
    @Autowired private DeviceTrustService deviceTrustService;
    @Autowired private BridgeTrustService bridgeTrustService;
    @Autowired private KeyLifecycleService keyLifecycleService;
    @Autowired private Ed25519CryptoService ed25519Service;
    @Autowired private ServerKeyHolder serverKey;
    @Autowired private HybridCryptoService crypto;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private IdempotencyService idempotency;

    @BeforeEach
    void clear() {
        idempotency.clear();
    }

    // 1. Valid device payment -> PASS
    @Test
    void testValidDevicePayment() throws Exception {
        deviceTrustService.registerDevice("dev-alice-001", "alice@demo", "MCowBQYDK2VwAyEA...");
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);

        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", 5);
        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);

        assertEquals("SETTLED", result.outcome());
    }

    // 2. Revoked device payment -> REJECTED
    @Test
    void testRevokedDevicePayment() throws Exception {
        deviceTrustService.registerDevice("dev-alice-revoked", "alice@demo", "MCowBQYDK2VwAyEA...");
        deviceTrustService.revokeDevice("dev-alice-revoked");

        Wallet wallet = walletService.getWalletByVpa("alice@demo");
        WalletAuthorization auth = walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        auth.setDeviceId("dev-alice-revoked");

        // Re-sign authorization with server key for dev-alice-revoked
        String serverSig = ed25519Service.sign(auth.getCanonicalData(), serverKey.getEd25519PrivateKey());
        auth.setServerSignature(serverSig);

        PaymentInstruction instruction = new PaymentInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", UUID.randomUUID().toString(), System.currentTimeMillis());
        instruction.setWalletAuthorization(auth);

        String deviceSig = ed25519Service.sign(instruction.getCanonicalData(), wallet.getDeviceKeyPair().getPrivate());
        instruction.setDeviceSignature(deviceSig);

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTransactionId(instruction.getTransactionId());
        packet.setWalletId(auth.getWalletId());
        packet.setCreatedAt(instruction.getSignedAt());
        packet.setCiphertext(crypto.encrypt(instruction, serverKey.getPublicKey(), packet.getCanonicalAad()));

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("REVOKED"));
    }

    // 3. Unknown device payment -> REJECTED
    @Test
    void testUnknownDevicePayment() throws Exception {
        Wallet wallet = walletService.getWalletByVpa("alice@demo");
        WalletAuthorization auth = walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        auth.setDeviceId("unknown-unregistered-device");

        // Re-sign authorization with server key for unknown-unregistered-device
        String serverSig = ed25519Service.sign(auth.getCanonicalData(), serverKey.getEd25519PrivateKey());
        auth.setServerSignature(serverSig);

        PaymentInstruction instruction = new PaymentInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", UUID.randomUUID().toString(), System.currentTimeMillis());
        instruction.setWalletAuthorization(auth);

        String deviceSig = ed25519Service.sign(instruction.getCanonicalData(), wallet.getDeviceKeyPair().getPrivate());
        instruction.setDeviceSignature(deviceSig);

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTransactionId(instruction.getTransactionId());
        packet.setWalletId(auth.getWalletId());
        packet.setCreatedAt(instruction.getSignedAt());
        packet.setCiphertext(crypto.encrypt(instruction, serverKey.getPublicKey(), packet.getCanonicalAad()));

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("Unknown or unregistered hardware device"));
    }

    // 4. Rotated key payment -> PASS (coexisting active keys)
    @Test
    void testRotatedKeyPayment() {
        keyLifecycleService.rotateKey("key-server-rsa-2048", "key-server-rsa-2048-v2", "RSA-2048/OAEP-SHA256", "SERVER_ENCRYPTION", "NEW_PUB_KEY");
        assertDoesNotThrow(() -> keyLifecycleService.validateKeyStatus("key-server-rsa-2048")); // Rotated key remains valid during transition
        assertDoesNotThrow(() -> keyLifecycleService.validateKeyStatus("key-server-rsa-2048-v2"));
    }

    // 5. Expired key payment -> REJECTED
    @Test
    void testExpiredKeyPayment() {
        keyLifecycleService.registerKey("key-expired-001", "RSA-2048", "SERVER_ENCRYPTION", "PUB_KEY", Instant.now().minusSeconds(100));

        Exception ex = assertThrows(Exception.class, () -> keyLifecycleService.validateKeyStatus("key-expired-001"));
        assertTrue(ex.getMessage().contains("EXPIRED"));
    }

    // 6. Forged signature payment -> REJECTED
    @Test
    void testForgedSignaturePayment() throws Exception {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        PaymentInstruction instruction = walletService.prepareOfflineInstruction("alice@demo", "bob@demo", new BigDecimal("50.00"), "1234");

        KeyPair attackerKeyPair = ed25519Service.generateKeyPair();
        String forgedSig = ed25519Service.sign(instruction.getCanonicalData(), attackerKeyPair.getPrivate());
        instruction.setDeviceSignature(forgedSig);

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTransactionId(instruction.getTransactionId());
        packet.setWalletId(instruction.getWalletAuthorization().getWalletId());
        packet.setCreatedAt(instruction.getSignedAt());
        packet.setCiphertext(crypto.encrypt(instruction, serverKey.getPublicKey(), packet.getCanonicalAad()));

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("Invalid device transaction signature"));
    }

    // 7. Unauthorized bridge -> REJECTED
    @Test
    void testUnauthorizedBridgePayment() throws Exception {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", 5);

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "unauthorized-malicious-bridge", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("unauthorized_bridge"));
    }
}
