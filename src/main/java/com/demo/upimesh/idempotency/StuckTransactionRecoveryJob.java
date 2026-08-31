package com.demo.upimesh.idempotency;

import com.demo.upimesh.observability.AuditLogger;
import com.demo.upimesh.persistence.Transaction;
import com.demo.upimesh.persistence.TransactionRepository;
import com.demo.upimesh.protocol.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job recovering transactions stuck in PROCESSING state due to node crashes or timeouts.
 */
@Service
public class StuckTransactionRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(StuckTransactionRecoveryJob.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private AuditLogger auditLogger;

    @Value("${upi.mesh.stuck-timeout-seconds:90}")
    private long stuckTimeoutSeconds = 90;

    @Scheduled(fixedDelayString = "${upi.mesh.recovery-interval-ms:60000}")
    @Transactional
    public void recoverStuckTransactions() {
        Instant cutoff = Instant.now().minusSeconds(stuckTimeoutSeconds);
        List<Transaction> stuck = transactionRepository.findByStateAndUpdatedAtBefore(
                TransactionState.PROCESSING, cutoff);

        if (stuck.isEmpty()) return;

        log.info("Found {} transaction(s) stuck in PROCESSING state past timeout ({}s)",
                stuck.size(), stuckTimeoutSeconds);

        for (Transaction tx : stuck) {
            log.warn("Recovering stuck transaction {} (packetHash={})", tx.getId(), tx.getPacketHash());
            tx.setState(TransactionState.FAILED_RETRYABLE);
            tx.setFailureReason("Transaction timed out in PROCESSING state; reset for retry");
            transactionRepository.save(tx);

            idempotencyService.release(tx.getPacketHash());
            auditLogger.recordEvent("RECOVERED", tx.getPacketHash(), "state_reset_to_FAILED_RETRYABLE");
        }
    }
}
