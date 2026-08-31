package com.demo.upimesh.bridge;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.idempotency.IdempotencyService;
import com.demo.upimesh.observability.AuditLogger;
import com.demo.upimesh.persistence.Transaction;
import com.demo.upimesh.persistence.TransactionRepository;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.TransactionState;
import com.demo.upimesh.protocol.exception.StalePacketException;
import com.demo.upimesh.security.BridgeTrustService;
import com.demo.upimesh.security.SecurityValidationService;
import com.demo.upimesh.settlement.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Orchestrates server-side ingestion for DTN bundles uploaded by bridge nodes.
 * Lifecycle: RECEIVED -> PROCESSING -> VALIDATED -> SETTLED
 */
@Service
public class BridgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestionService.class);

    @Autowired private HybridCryptoService crypto;
    @Autowired private IdempotencyService idempotency;
    @Autowired private SecurityValidationService securityValidation;
    @Autowired private SettlementService settlement;
    @Autowired private TransactionRepository transactions;
    @Autowired private AuditLogger auditLogger;
    @Autowired private BridgeTrustService bridgeTrustService;

    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount) {
        try {
            if (packet == null) {
                return IngestResult.invalid("?", "null_packet");
            }

            auditLogger.recordEvent("INGESTED", packet.getPacketId(), "bridge=" + bridgeNodeId);

            // 0. Bridge Identity & Trust Validation
            try {
                bridgeTrustService.validateBridgeTrust(bridgeNodeId);
            } catch (Exception e) {
                log.warn("Unauthorized or revoked bridge node upload attempted: {} ({})", bridgeNodeId, e.getMessage());
                auditLogger.recordEvent("UNAUTHORIZED_BRIDGE", packet.getPacketId(), "bridge=" + bridgeNodeId);
                return IngestResult.invalid("?", "unauthorized_bridge: " + e.getMessage());
            }

            // 1. Key ID / Algorithm Fast-Path Validation
            try {
                crypto.validateKeyId(packet.getKeyId());
            } catch (Exception e) {
                log.warn("Key ID validation failed for packet {}: {}", packet.getPacketId(), e.getMessage());
                auditLogger.recordEvent("INVALID", packet.getPacketId(), "unsupported_key_id");
                return IngestResult.invalid("?", "unsupported_key_id: " + e.getMessage());
            }

            String packetHash = crypto.hashCiphertext(packet.getCiphertext());

            // 2. Layer 1: Distributed Atomic Idempotency Claim (Redis SET-NX)
            IdempotencyService.ClaimResult claim = idempotency.claim(packetHash);
            if (claim == IdempotencyService.ClaimResult.DUPLICATE) {
                Optional<Transaction> existing = idempotency.getExistingTransaction(packetHash);
                if (existing.isPresent()) {
                    Transaction tx = existing.get();
                    log.info("DUPLICATE packet {} retry (state={}) — returning existing transaction result",
                            packetHash.substring(0, Math.min(12, packetHash.length())), tx.getState());
                    auditLogger.recordEvent("DUPLICATE_DROPPED", packetHash, "state=" + tx.getState());
                    return IngestResult.duplicate(packetHash, tx);
                }
                auditLogger.recordEvent("DUPLICATE_DROPPED", packetHash, "bridge=" + bridgeNodeId);
                return IngestResult.duplicate(packetHash);
            }

            // 3. Record PROCESSING State in Database (Layer 2: PostgreSQL UNIQUE Constraint Backstop)
            Transaction tx = new Transaction();
            tx.setPacketHash(packetHash);
            tx.setBridgeNodeId(bridgeNodeId);
            tx.setHopCount(hopCount);
            tx.setState(TransactionState.PROCESSING);

            try {
                tx = transactions.save(tx);
            } catch (DataIntegrityViolationException e) {
                // DB UNIQUE constraint caught race condition
                log.warn("DB Unique Constraint caught concurrent ingestion race for {}", packetHash);
                idempotency.markState(packetHash, TransactionState.DUPLICATE);
                Optional<Transaction> existing = transactions.findByPacketHash(packetHash);
                return existing.map(value -> IngestResult.duplicate(packetHash, value))
                        .orElseGet(() -> IngestResult.duplicate(packetHash));
            }

            // 4. Decrypt & AES-GCM AAD Verification
            PaymentInstruction instruction;
            try {
                byte[] aadBytes = packet.getCanonicalAad();
                instruction = crypto.decrypt(packet.getCiphertext(), aadBytes);
            } catch (Exception e) {
                log.warn("Decryption / AAD verification failed for packet {}: {}",
                        packetHash.substring(0, Math.min(12, packetHash.length())), e.getMessage());
                tx.setState(TransactionState.FAILED_PERMANENT);
                tx.setFailureReason("decryption_or_aad_failed: " + e.getMessage());
                transactions.save(tx);
                idempotency.markState(packetHash, TransactionState.FAILED_PERMANENT);
                auditLogger.recordEvent("INVALID", packetHash, "decryption_or_aad_failed");
                return IngestResult.invalid(packetHash, "decryption_or_aad_failed: " + e.getMessage());
            }

            // 5. Security & Protocol Validation
            try {
                securityValidation.validateInstruction(instruction);
            } catch (StalePacketException e) {
                log.warn("Validation expired for packet {}: {}",
                        packetHash.substring(0, Math.min(12, packetHash.length())), e.getMessage());
                tx.setState(TransactionState.EXPIRED);
                tx.setFailureReason("EXPIRED: " + e.getMessage());
                transactions.save(tx);
                idempotency.markState(packetHash, TransactionState.EXPIRED);
                auditLogger.recordEvent("EXPIRED", packetHash, e.getMessage());
                return IngestResult.invalid(packetHash, "EXPIRED: " + e.getMessage());
            } catch (Exception e) {
                log.warn("Validation failed for packet {}: {}",
                        packetHash.substring(0, Math.min(12, packetHash.length())), e.getMessage());
                tx.setState(TransactionState.REJECTED);
                tx.setFailureReason(e.getMessage());
                transactions.save(tx);
                idempotency.markState(packetHash, TransactionState.REJECTED);
                auditLogger.recordEvent("REJECTED", packetHash, e.getMessage());
                return IngestResult.invalid(packetHash, e.getMessage());
            }

            // 6. Transition to VALIDATED
            tx.setState(TransactionState.VALIDATED);
            tx = transactions.save(tx);
            idempotency.markState(packetHash, TransactionState.VALIDATED);

            // 7. Atomic Settlement (VALIDATED -> SETTLED)
            Transaction settledTx = settlement.settle(instruction, packetHash, bridgeNodeId, hopCount);
            idempotency.markState(packetHash, TransactionState.SETTLED);

            return IngestResult.settled(packetHash, settledTx);

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
        public static IngestResult duplicate(String hash, Transaction tx) {
            return new IngestResult("DUPLICATE_DROPPED", hash, "DUPLICATE", tx != null ? tx.getId() : null);
        }
        public static IngestResult duplicate(String hash) {
            return new IngestResult("DUPLICATE_DROPPED", hash, "DUPLICATE", null);
        }
        public static IngestResult invalid(String hash, String reason) {
            return new IngestResult("INVALID", hash, reason, null);
        }
    }
}
