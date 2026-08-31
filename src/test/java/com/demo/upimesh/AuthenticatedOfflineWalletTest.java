package com.demo.upimesh;

import com.demo.upimesh.bridge.BridgeIngestionService;
import com.demo.upimesh.crypto.Ed25519CryptoService;
import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.idempotency.IdempotencyService;
import com.demo.upimesh.persistence.AccountRepository;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.WalletAuthorization;
import com.demo.upimesh.protocol.exception.CryptographicValidationException;
import com.demo.upimesh.protocol.exception.InsufficientOfflineBalanceException;
import com.demo.upimesh.protocol.exception.StalePacketException;
import com.demo.upimesh.security.SecurityValidationService;
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
class AuthenticatedOfflineWalletTest {

    @Autowired private WalletService walletService;
    @Autowired private SecurityValidationService securityValidation;
    @Autowired private Ed25519CryptoService ed25519Service;
    @Autowired private ServerKeyHolder serverKey;
    @Autowired private HybridCryptoService crypto;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private IdempotencyService idempotency;
    @Autowired private AccountRepository accounts;

    @BeforeEach
    void clear() {
        idempotency.clear();
    }

    private MeshPacket createPacketWithMatchingAad(PaymentInstruction instruction) throws Exception {
        MeshPacket packet = new MeshPacket();
        packet.setVersion(instruction.getVersion());
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTransactionId(instruction.getTransactionId());
        packet.setWalletId(instruction.getWalletAuthorization() == null ? "wlet-alice" : instruction.getWalletAuthorization().getWalletId());
        packet.setKeyId(HybridCryptoService.SUPPORTED_KEY_ID);
        packet.setOriginDeviceId("phone-alice");
        packet.setTtl(5);
        packet.setCreatedAt(instruction.getSignedAt());

        byte[] aadBytes = packet.getCanonicalAad();
        String ciphertext = crypto.encrypt(instruction, serverKey.getPublicKey(), aadBytes);
        packet.setCiphertext(ciphertext);

        return packet;
    }

    // 1. Valid wallet issuance
    @Test
    void testValidWalletIssuance() {
        WalletAuthorization auth = walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);

        assertNotNull(auth);
        assertEquals("alice@demo", auth.getAccountVpa());
        assertEquals(0, new BigDecimal("500.00").compareTo(auth.getAuthorizedBalance()));

        boolean validServerSig = ed25519Service.verify(
                auth.getCanonicalData(),
                auth.getServerSignature(),
                serverKey.getEd25519PublicKey()
        );
        assertTrue(validServerSig, "Server authorization signature must be valid");
    }

    // 2. Valid offline payment
    @Test
    void testValidOfflinePayment() throws Exception {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        PaymentInstruction instruction = walletService.prepareOfflineInstruction(
                "alice@demo", "bob@demo", new BigDecimal("100.00"), "1234");

        assertDoesNotThrow(() -> securityValidation.validateInstruction(instruction));

        MeshPacket packet = createPacketWithMatchingAad(instruction);

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("SETTLED", result.outcome());
    }

    // 3. Insufficient offline balance
    @Test
    void testInsufficientOfflineBalance() {
        Wallet wallet = walletService.getWalletByVpa("alice@demo");
        BigDecimal currentBalance = wallet.getAvailableOfflineBalance();

        assertThrows(InsufficientOfflineBalanceException.class, () -> {
            walletService.prepareOfflineInstruction(
                    "alice@demo", "bob@demo", currentBalance.add(new BigDecimal("100.00")), "1234");
        });
    }

    // 4. Expired wallet authorization
    @Test
    void testExpiredWalletAuthorization() {
        WalletAuthorization auth = walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), -10);

        PaymentInstruction instruction = new PaymentInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", UUID.randomUUID().toString(), System.currentTimeMillis());
        instruction.setWalletAuthorization(auth);

        KeyPair deviceKeyPair = walletService.getWalletByVpa("alice@demo").getDeviceKeyPair();
        String deviceSig = ed25519Service.sign(instruction.getCanonicalData(), deviceKeyPair.getPrivate());
        instruction.setDeviceSignature(deviceSig);

        assertThrows(StalePacketException.class, () -> securityValidation.validateInstruction(instruction));
    }

    // 5. Modified amount
    @Test
    void testModifiedAmountTampering() {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        PaymentInstruction instruction = walletService.prepareOfflineInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234");

        instruction.setAmount(new BigDecimal("500.00"));

        assertThrows(CryptographicValidationException.class, () -> securityValidation.validateInstruction(instruction));
    }

    // 6. Modified receiver
    @Test
    void testModifiedReceiverTampering() {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        PaymentInstruction instruction = walletService.prepareOfflineInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234");

        instruction.setReceiverVpa("carol@demo");

        assertThrows(CryptographicValidationException.class, () -> securityValidation.validateInstruction(instruction));
    }

    // 7. Modified nonce
    @Test
    void testModifiedNonceTampering() {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        PaymentInstruction instruction = walletService.prepareOfflineInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234");

        instruction.setNonce(UUID.randomUUID().toString());

        assertThrows(CryptographicValidationException.class, () -> securityValidation.validateInstruction(instruction));
    }

    // 8. Forged signature
    @Test
    void testForgedDeviceSignature() {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        PaymentInstruction instruction = walletService.prepareOfflineInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234");

        KeyPair attackerKeys = ed25519Service.generateKeyPair();
        String forgedSig = ed25519Service.sign(instruction.getCanonicalData(), attackerKeys.getPrivate());
        instruction.setDeviceSignature(forgedSig);

        assertThrows(CryptographicValidationException.class, () -> securityValidation.validateInstruction(instruction));
    }

    // 9. Invalid wallet authorization
    @Test
    void testInvalidWalletAuthorizationServerSignature() {
        WalletAuthorization auth = walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);

        auth.setAuthorizedBalance(new BigDecimal("9999.00"));

        PaymentInstruction instruction = new PaymentInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", UUID.randomUUID().toString(), System.currentTimeMillis());
        instruction.setWalletAuthorization(auth);

        KeyPair deviceKeyPair = walletService.getWalletByVpa("alice@demo").getDeviceKeyPair();
        String deviceSig = ed25519Service.sign(instruction.getCanonicalData(), deviceKeyPair.getPrivate());
        instruction.setDeviceSignature(deviceSig);

        assertThrows(CryptographicValidationException.class, () -> securityValidation.validateInstruction(instruction));
    }

    // 10. Replayed transaction
    @Test
    void testReplayedTransactionDropped() throws Exception {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        PaymentInstruction instruction = walletService.prepareOfflineInstruction(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234");

        MeshPacket packet = createPacketWithMatchingAad(instruction);

        BridgeIngestionService.IngestResult r1 = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("SETTLED", r1.outcome());

        BridgeIngestionService.IngestResult r2 = bridge.ingest(packet, "bridge-2", 2);
        assertEquals("DUPLICATE_DROPPED", r2.outcome());
    }
}
