package com.demo.upimesh.bridge;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.idempotency.IdempotencyService;
import com.demo.upimesh.observability.AuditLogger;
import com.demo.upimesh.persistence.Transaction;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.security.SecurityValidationService;
import com.demo.upimesh.settlement.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orchestrates server-side ingestion for DTN bundles uploaded by bridge nodes.
 */
@Service
public class BridgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestionService.class);

    @Autowired private HybridCryptoService crypto;
    @Autowired private IdempotencyService idempotency;
    @Autowired private SecurityValidationService securityValidation;
    @Autowired private SettlementService settlement;
    @Autowired private AuditLogger auditLogger;

    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount) {
        try {
            if (packet == null) {
                return IngestResult.invalid("?", "null_packet");
            }

            auditLogger.recordEvent("INGESTED", packet.getPacketId(), "bridge=" + bridgeNodeId);

            // 1. Key ID / Algorithm Validation
            try {
                crypto.validateKeyId(packet.getKeyId());
            } catch (Exception e) {
                log.warn("Key ID validation failed for packet {}: {}", packet.getPacketId(), e.getMessage());
                auditLogger.recordEvent("INVALID", packet.getPacketId(), "unsupported_key_id");
                return IngestResult.invalid("?", "unsupported_key_id: " + e.getMessage());
            }

            String packetHash = crypto.hashCiphertext(packet.getCiphertext());

            // 2. Idempotency Gate
            if (!idempotency.claim(packetHash)) {
                log.info("DUPLICATE packet {} from bridge {} — dropped",
                        packetHash.substring(0, Math.min(12, packetHash.length())) + "...", bridgeNodeId);
                auditLogger.recordEvent("DUPLICATE_DROPPED", packetHash, "bridge=" + bridgeNodeId);
                return IngestResult.duplicate(packetHash);
            }

            // 3. Decrypt with AES-GCM Additional Authenticated Data (AAD) Verification
            PaymentInstruction instruction;
            try {
                byte[] aadBytes = packet.getCanonicalAad();
                instruction = crypto.decrypt(packet.getCiphertext(), aadBytes);
            } catch (Exception e) {
                log.warn("Decryption / AAD verification failed for packet {}: {}",
                        packetHash.substring(0, Math.min(12, packetHash.length())) + "...", e.getMessage());
                auditLogger.recordEvent("INVALID", packetHash, "decryption_or_aad_failed");
                return IngestResult.invalid(packetHash, "decryption_or_aad_failed: " + e.getMessage());
            }

            // 4. Security & Protocol Validation
            try {
                securityValidation.validateInstruction(instruction);
            } catch (Exception e) {
                log.warn("Validation failed for packet {}: {}",
                        packetHash.substring(0, Math.min(12, packetHash.length())) + "...", e.getMessage());
                auditLogger.recordEvent("INVALID", packetHash, e.getMessage());
                return IngestResult.invalid(packetHash, e.getMessage());
            }

            // 5. Settle
            Transaction tx = settlement.settle(instruction, packetHash, bridgeNodeId, hopCount);
            return IngestResult.settled(packetHash, tx);

        } catch (Exception e) {
            log.error("Ingestion error: {}", e.getMessage(), e);
            auditLogger.recordEvent("INVALID", "?", "internal_error: " + e.getMessage());
            return IngestResult.invalid("?", "internal_error: " + e.getMessage());
        }
    }

    public record IngestResult(String outcome, String packetHash, String reason, Long transactionId) {
        public static IngestResult settled(String hash, Transaction tx) {
            return new IngestResult("SETTLED", hash, null, tx.getId());
        }
        public static IngestResult duplicate(String hash) {
            return new IngestResult("DUPLICATE_DROPPED", hash, null, null);
        }
        public static IngestResult invalid(String hash, String reason) {
            return new IngestResult("INVALID", hash, reason, null);
        }
    }
}
