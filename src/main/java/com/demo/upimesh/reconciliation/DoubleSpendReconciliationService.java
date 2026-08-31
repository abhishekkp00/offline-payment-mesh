package com.demo.upimesh.reconciliation;

import com.demo.upimesh.observability.AuditLogger;
import com.demo.upimesh.persistence.Transaction;
import com.demo.upimesh.persistence.TransactionEventEntity;
import com.demo.upimesh.persistence.WalletSpendEntity;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.TransactionClassification;
import com.demo.upimesh.protocol.TransactionState;
import com.demo.upimesh.protocol.WalletAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Subsystem detecting offline double spending, cumulative allowance exhaustion, and conflicting transactions.
 */
@Service
public class DoubleSpendReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(DoubleSpendReconciliationService.class);

    @Autowired
    private AuditLogger auditLogger;

    // Track spend commitments per walletId
    private final Map<String, List<WalletSpendEntity>> walletSpendRegistry = new ConcurrentHashMap<>();
    private final Map<String, String> noncesSeen = new ConcurrentHashMap<>();

    public ReconciliationResult reconcile(PaymentInstruction instruction, String packetHash,
                                          String bridgeNodeId, int hopCount, ReconciliationPolicy policy) {

        WalletAuthorization auth = instruction.getWalletAuthorization();
        String walletId = auth != null ? auth.getWalletId() : "wlet-" + instruction.getSenderVpa();
        BigDecimal authorizedLimit = auth != null && auth.getAuthorizedBalance() != null ?
                auth.getAuthorizedBalance() : new BigDecimal("5000.00");

        String nonce = instruction.getNonce();
        BigDecimal amount = instruction.getAmount();

        // 1. Replay Attack Check (Reused Nonce)
        if (nonce != null && noncesSeen.putIfAbsent(nonce, packetHash) != null) {
            String prevHash = noncesSeen.get(nonce);
            if (!prevHash.equalsIgnoreCase(packetHash)) {
                log.warn("REPLAY ATTACK DETECTED: Nonce {} already used in packet {}", nonce, prevHash);
                auditLogger.recordEvent("REPLAY_ATTACK", packetHash, "nonce=" + nonce + " prevHash=" + prevHash);
                return new ReconciliationResult(
                        TransactionClassification.REPLAY_ATTACK,
                        TransactionState.EXPIRED,
                        "REPLAY_ATTACK: Nonce " + nonce + " already consumed"
                );
            }
        }

        // 2. Cumulative Spend Calculation
        List<WalletSpendEntity> spends = walletSpendRegistry.computeIfAbsent(walletId, k -> new CopyOnWriteArrayList<>());
        BigDecimal cumulativeSpent = spends.stream()
                .filter(s -> "COMMITTED".equalsIgnoreCase(s.getStatus()))
                .map(WalletSpendEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal availableBalance = authorizedLimit.subtract(cumulativeSpent);

        log.info("Reconciling tx {} for wallet {}: Requested ₹{}, CumulativeSpent ₹{}, Available ₹{}, Limit ₹{}",
                instruction.getTransactionId(), walletId, amount, cumulativeSpent, availableBalance, authorizedLimit);

        // 3. Evaluation: Within Authorized Balance -> Legitimate Settlement
        if (amount.compareTo(availableBalance) <= 0) {
            WalletSpendEntity spendRecord = new WalletSpendEntity();
            spendRecord.setWalletId(walletId);
            spendRecord.setTransactionId(instruction.getTransactionId());
            spendRecord.setAmount(amount);
            spendRecord.setReservedAt(Instant.now());
            spendRecord.setCommittedAt(Instant.now());
            spendRecord.setStatus("COMMITTED");
            spends.add(spendRecord);

            auditLogger.recordEvent("RECONCILED_SETTLED", packetHash,
                    "walletId=" + walletId + " amount=" + amount + " remaining=" + availableBalance.subtract(amount));

            return new ReconciliationResult(
                    TransactionClassification.LEGITIMATE_SETTLED,
                    TransactionState.SETTLED,
                    null
            );
        }

        // 4. Evaluation: Exceeds Balance -> Conflicting Double Spend vs Overspending
        if (cumulativeSpent.signum() > 0) {
            // Multiple distinct offline transactions spent against the same wallet allowance
            log.warn("CONFLICTING OFFLINE DOUBLE-SPEND DETECTED for wallet {}: cumulative ₹{} + requested ₹{} > limit ₹{}",
                    walletId, cumulativeSpent, amount, authorizedLimit);

            auditLogger.recordEvent("CONFLICTED_DOUBLE_SPEND", packetHash,
                    "walletId=" + walletId + " cumulativeSpent=" + cumulativeSpent + " requested=" + amount + " limit=" + authorizedLimit);

            return new ReconciliationResult(
                    TransactionClassification.CONFLICTING_OFFLINE_TRANSACTIONS,
                    TransactionState.CONFLICTED,
                    "CONFLICTED_OFFLINE_DOUBLE_SPEND: Cumulative spend ₹" + cumulativeSpent.add(amount) + " exceeds limit ₹" + authorizedLimit
            );
        } else {
            // Single transaction amount exceeds authorized limit
            log.warn("OVERSPENDING DETECTED for wallet {}: requested ₹{} > limit ₹{}", walletId, amount, authorizedLimit);

            auditLogger.recordEvent("OVERSPENT_AUTHORIZATION", packetHash,
                    "walletId=" + walletId + " requested=" + amount + " limit=" + authorizedLimit);

            return new ReconciliationResult(
                    TransactionClassification.OVERSPENDING,
                    TransactionState.OVERSPENT,
                    "OVERSPENT_AUTHORIZATION: Requested amount ₹" + amount + " exceeds limit ₹" + authorizedLimit
            );
        }
    }

    public void clear() {
        walletSpendRegistry.clear();
        noncesSeen.clear();
    }

    public record ReconciliationResult(
            TransactionClassification classification,
            TransactionState targetState,
            String reason
    ) {}
}
