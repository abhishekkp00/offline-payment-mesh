package com.demo.upimesh;

import com.demo.upimesh.bridge.BridgeIngestionService;
import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.idempotency.IdempotencyService;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.simulator.DemoService;
import com.demo.upimesh.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PacketAadSecurityTest {

    @Autowired private DemoService demoService;
    @Autowired private WalletService walletService;
    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private IdempotencyService idempotency;

    @BeforeEach
    void clear() {
        idempotency.clear();
    }

    private MeshPacket buildValidPacket() throws Exception {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        return demoService.createPacket("alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", 5);
    }

    // 1. Modification of protocolVersion -> rejection
    @Test
    void testTamperedProtocolVersionRejected() throws Exception {
        MeshPacket packet = buildValidPacket();
        packet.setVersion("2.0.0"); // Tamper unencrypted version

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("decryption_or_aad_failed") || result.reason().contains("Incompatible protocol version"));
    }

    // 2. Modification of packetId -> rejection (AAD tag mismatch)
    @Test
    void testTamperedPacketIdRejected() throws Exception {
        MeshPacket packet = buildValidPacket();
        packet.setPacketId(UUID.randomUUID().toString()); // Tamper unencrypted packetId

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("decryption_or_aad_failed"));
    }

    // 3. Modification of transactionId -> rejection (AAD tag mismatch)
    @Test
    void testTamperedTransactionIdRejected() throws Exception {
        MeshPacket packet = buildValidPacket();
        packet.setTransactionId(UUID.randomUUID().toString()); // Tamper unencrypted transactionId

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("decryption_or_aad_failed"));
    }

    // 4. Modification of walletId -> rejection (AAD tag mismatch)
    @Test
    void testTamperedWalletIdRejected() throws Exception {
        MeshPacket packet = buildValidPacket();
        packet.setWalletId("wlet-malicious-attacker"); // Tamper unencrypted walletId

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("decryption_or_aad_failed"));
    }

    // 5. Modification of createdAt -> rejection (AAD tag mismatch)
    @Test
    void testTamperedCreatedAtRejected() throws Exception {
        MeshPacket packet = buildValidPacket();
        packet.setCreatedAt(packet.getCreatedAt() + 1000); // Tamper unencrypted timestamp

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("decryption_or_aad_failed"));
    }

    // 6. Modification of keyId -> rejection (Unsupported key algorithm/key ID)
    @Test
    void testTamperedKeyIdRejected() throws Exception {
        MeshPacket packet = buildValidPacket();
        packet.setKeyId("key-unknown-algorithm-rsa-1024"); // Algorithm confusion attempt

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("unsupported_key_id"));
    }

    // 7. Modification of ciphertext -> rejection (GCM tag mismatch / AES decryption failure)
    @Test
    void testTamperedCiphertextRejected() throws Exception {
        MeshPacket packet = buildValidPacket();
        char[] chars = packet.getCiphertext().toCharArray();
        chars[chars.length / 2] = chars[chars.length / 2] == 'A' ? 'B' : 'A';
        packet.setCiphertext(new String(chars));

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("decryption_or_aad_failed"));
    }

    // 8. Oversized packet (> 64 KB limit) -> rejection
    @Test
    void testOversizedPacketRejected() throws Exception {
        MeshPacket packet = buildValidPacket();
        char[] hugePayload = new char[70000];
        Arrays.fill(hugePayload, 'A');
        packet.setCiphertext(new String(hugePayload));

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("maximum allowed size limit"));
    }

    // 9. Malformed Base64 payload -> rejection
    @Test
    void testMalformedBase64Rejected() throws Exception {
        MeshPacket packet = buildValidPacket();
        packet.setCiphertext("!!!not-valid-base64!!!");

        BridgeIngestionService.IngestResult result = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("INVALID", result.outcome());
        assertTrue(result.reason().contains("decryption_or_aad_failed") || result.reason().contains("Malformed Base64"));
    }
}
