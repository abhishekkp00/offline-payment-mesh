package com.demo.upimesh.settlement;

import com.demo.upimesh.observability.AuditLogger;
import com.demo.upimesh.persistence.Account;
import com.demo.upimesh.persistence.AccountRepository;
import com.demo.upimesh.persistence.Transaction;
import com.demo.upimesh.persistence.TransactionRepository;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.TransactionState;
import com.demo.upimesh.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Handles atomic balance settlement and ledger transaction updates.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private WalletService walletService;
    @Autowired private AuditLogger auditLogger;

    @Transactional
    public Transaction settle(PaymentInstruction instruction, String packetHash,
                              String bridgeNodeId, int hopCount) {

        Account sender = accounts.findById(instruction.getSenderVpa())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown sender VPA: " + instruction.getSenderVpa()));

        Account receiver = accounts.findById(instruction.getReceiverVpa())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown receiver VPA: " + instruction.getReceiverVpa()));

        BigDecimal amount = instruction.getAmount();
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Transaction tx = transactions.findByPacketHash(packetHash).orElseGet(Transaction::new);
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(amount);
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);

        if (sender.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance: {} has ₹{}, tried to send ₹{}",
                    sender.getVpa(), sender.getBalance(), amount);
            auditLogger.recordEvent("REJECTED", packetHash, "insufficient_balance");
            tx.setState(TransactionState.REJECTED);
            tx.setFailureReason("insufficient_balance");
            tx.setSettledAt(Instant.now());
            return transactions.save(tx);
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accounts.save(sender);
        accounts.save(receiver);

        // Commit wallet reservation
        walletService.getWalletByVpa(sender.getVpa()).commitReservation(amount);

        tx.setSettledAt(Instant.now());
        tx.setState(TransactionState.SETTLED);
        tx.setFailureReason(null);
        transactions.save(tx);

        auditLogger.recordEvent("SETTLED", packetHash, "amount=" + amount + " sender=" + sender.getVpa());

        log.info("SETTLED ₹{} from {} to {} (packetHash={}, bridge={}, hops={})",
                amount, sender.getVpa(), receiver.getVpa(),
                packetHash.substring(0, Math.min(12, packetHash.length())) + "...", bridgeNodeId, hopCount);

        return tx;
    }

    @Transactional
    public Transaction recordRejected(PaymentInstruction instruction, String packetHash,
                                       String bridgeNodeId, int hopCount, String reason) {
        Transaction tx = transactions.findByPacketHash(packetHash).orElseGet(Transaction::new);
        tx.setPacketHash(packetHash);
        if (instruction != null) {
            tx.setSenderVpa(instruction.getSenderVpa());
            tx.setReceiverVpa(instruction.getReceiverVpa());
            tx.setAmount(instruction.getAmount());
            if (instruction.getSignedAt() > 0) {
                tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
            }
        }
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setState(TransactionState.REJECTED);
        tx.setFailureReason(reason);
        return transactions.save(tx);
    }
}
