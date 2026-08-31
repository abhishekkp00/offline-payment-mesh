package com.demo.upimesh;

import com.demo.upimesh.bridge.BridgeIngestionService;
import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.idempotency.IdempotencyService;
import com.demo.upimesh.idempotency.StuckTransactionRecoveryJob;
import com.demo.upimesh.persistence.AccountRepository;
import com.demo.upimesh.persistence.Transaction;
import com.demo.upimesh.persistence.TransactionRepository;
import com.demo.upimesh.protocol.TransactionState;
import com.demo.upimesh.simulator.DemoService;
import com.demo.upimesh.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DistributedIdempotencyTest {

    @Autowired private DemoService demoService;
    @Autowired private WalletService walletService;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private IdempotencyService idempotency;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private StuckTransactionRecoveryJob recoveryJob;

    @BeforeEach
    void clear() {
        idempotency.clear();
    }

    // N=20 simultaneous submissions -> exactly 1 settlement, N-1 duplicate responses
    @Test
    void testNDuplicateSubmissionsYieldExactlyOneSettlement() throws Exception {
        int n = 20; // 20 concurrent threads submitting identical packet simultaneously
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("1000.00"), 3600);

        BigDecimal aliceBefore = accounts.findById("alice@demo").orElseThrow().getBalance();
        BigDecimal bobBefore = accounts.findById("bob@demo").orElseThrow().getBalance();

        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("100.00"), "1234", 5);

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger settledCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        Future<?>[] futures = new Future[n];
        for (int i = 0; i < n; i++) {
            final String node = "bridge-" + i;
            futures[i] = pool.submit(() -> {
                try {
                    startSignal.await(); // Synchronize all 20 threads to submit at the exact same millisecond
                    BridgeIngestionService.IngestResult r = bridge.ingest(packet, node, 3);
                    if ("SETTLED".equals(r.outcome())) {
                        settledCount.incrementAndGet();
                    } else if ("DUPLICATE_DROPPED".equals(r.outcome())) {
                        duplicateCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        startSignal.countDown(); // Fire all 20 threads concurrently
        for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, settledCount.get(), "N duplicate submissions must yield EXACTLY 1 settlement");
        assertEquals(n - 1, duplicateCount.get(), "N duplicate submissions must yield EXACTLY N-1 duplicate responses");

        // Verify balances modified exactly once
        BigDecimal aliceAfter = accounts.findById("alice@demo").orElseThrow().getBalance();
        BigDecimal bobAfter = accounts.findById("bob@demo").orElseThrow().getBalance();
        assertEquals(aliceBefore.subtract(new BigDecimal("100.00")), aliceAfter, "Sender balance debited exactly once");
        assertEquals(bobBefore.add(new BigDecimal("100.00")), bobAfter, "Receiver balance credited exactly once");
    }

    // Retrying a settled transaction returns DUPLICATE_DROPPED with existing transaction ID
    @Test
    void testRetryOfSettledTransactionReturnsExistingResult() throws Exception {
        walletService.issueWalletAuthorization("alice@demo", new BigDecimal("500.00"), 3600);
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", 5);

        BridgeIngestionService.IngestResult r1 = bridge.ingest(packet, "bridge-1", 1);
        assertEquals("SETTLED", r1.outcome());

        // Second submission of settled packet returns DUPLICATE_DROPPED containing original settlement transactionId
        BridgeIngestionService.IngestResult r2 = bridge.ingest(packet, "bridge-2", 2);
        assertEquals("DUPLICATE_DROPPED", r2.outcome());
        assertEquals(r1.transactionId(), r2.transactionId());
    }

    // Stuck TRANSACTION in PROCESSING state is recovered by StuckTransactionRecoveryJob
    @Test
    void testStuckTransactionRecoveryJob() {
        Transaction stuckTx = new Transaction();
        stuckTx.setPacketHash("stuck-hash-123");
        stuckTx.setBridgeNodeId("bridge-stuck");
        stuckTx.setHopCount(2);
        stuckTx.setState(TransactionState.PROCESSING);
        stuckTx.setUpdatedAt(Instant.now().minusSeconds(120)); // Past 90s threshold
        transactions.save(stuckTx);

        recoveryJob.recoverStuckTransactions();

        Transaction recovered = transactions.findByPacketHash("stuck-hash-123").orElseThrow();
        assertEquals(TransactionState.FAILED_RETRYABLE, recovered.getState());
        assertTrue(recovered.getFailureReason().contains("timed out in PROCESSING state"));
    }
}
