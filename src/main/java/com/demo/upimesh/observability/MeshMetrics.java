package com.demo.upimesh.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production Micrometer Metrics registry bean for Prometheus observability.
 */
@Component
public class MeshMetrics {

    private final MeterRegistry registry;

    private final Counter paymentsCreated;
    private final Counter paymentsReceived;
    private final Counter paymentsSettled;
    private final Counter paymentsRejected;
    private final Counter duplicateTransactions;
    private final Counter replayAttempts;
    private final Counter forgedSignatures;
    private final Counter expiredWallets;
    private final Counter conflictingSpends;
    private final Counter packetDrops;
    private final Counter packetDuplicates;
    private final Counter walletReconciliationFailures;

    private final Map<String, Counter> bridgeLoadCounters = new ConcurrentHashMap<>();

    private final AtomicReference<Double> averageHopsValue = new AtomicReference<>(0.0);
    private final AtomicReference<Double> deliveryRateValue = new AtomicReference<>(0.0);

    private final Timer settlementLatencyTimer;

    public MeshMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.paymentsCreated = Counter.builder("payments_created")
                .description("Total payment instructions generated offline").register(registry);

        this.paymentsReceived = Counter.builder("payments_received")
                .description("Total bundle packets ingested by server").register(registry);

        this.paymentsSettled = Counter.builder("payments_settled")
                .description("Total payment transactions settled in ledger").register(registry);

        this.paymentsRejected = Counter.builder("payments_rejected")
                .description("Total payment transactions rejected").register(registry);

        this.duplicateTransactions = Counter.builder("duplicate_transactions")
                .description("Total duplicate transaction submissions dropped").register(registry);

        this.replayAttempts = Counter.builder("replay_attempts")
                .description("Total replayed nonce / stale timestamp attempts").register(registry);

        this.forgedSignatures = Counter.builder("forged_signatures")
                .description("Total forged signature attempts").register(registry);

        this.expiredWallets = Counter.builder("expired_wallets")
                .description("Total expired wallet token attempts").register(registry);

        this.conflictingSpends = Counter.builder("conflicting_spends")
                .description("Total offline double-spend conflict attempts").register(registry);

        this.packetDrops = Counter.builder("packet_drops")
                .description("Total DTN buffer drops due to TTL or overflow").register(registry);

        this.packetDuplicates = Counter.builder("packet_duplicates")
                .description("Total redundant DTN hop packets").register(registry);

        this.walletReconciliationFailures = Counter.builder("wallet_reconciliation_failures")
                .description("Total cumulative wallet reconciliation failures").register(registry);

        this.settlementLatencyTimer = Timer.builder("settlement_latency")
                .description("End-to-end settlement processing latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        Gauge.builder("average_hops", averageHopsValue, AtomicReference::get)
                .description("Moving average hop count of settled transactions").register(registry);

        Gauge.builder("mesh_delivery_rate", deliveryRateValue, AtomicReference::get)
                .description("Percentage of generated vs settled transactions").register(registry);
    }

    public void incrementPaymentsCreated() { paymentsCreated.increment(); }
    public void incrementPaymentsReceived() { paymentsReceived.increment(); }
    public void incrementPaymentsSettled() { paymentsSettled.increment(); }
    public void incrementPaymentsRejected() { paymentsRejected.increment(); }
    public void incrementDuplicateTransactions() { duplicateTransactions.increment(); }
    public void incrementReplayAttempts() { replayAttempts.increment(); }
    public void incrementForgedSignatures() { forgedSignatures.increment(); }
    public void incrementExpiredWallets() { expiredWallets.increment(); }
    public void incrementConflictingSpends() { conflictingSpends.increment(); }
    public void incrementPacketDrops() { packetDrops.increment(); }
    public void incrementPacketDuplicates() { packetDuplicates.increment(); }
    public void incrementWalletReconciliationFailures() { walletReconciliationFailures.increment(); }

    public void incrementBridgeLoad(String bridgeId) {
        bridgeLoadCounters.computeIfAbsent(bridgeId, id ->
                Counter.builder("bridge_load")
                        .tag("bridgeId", id)
                        .description("Total ingestion uploads per bridge node")
                        .register(registry)
        ).increment();
    }

    public Timer getSettlementLatencyTimer() { return settlementLatencyTimer; }

    public void updateAverageHops(double avgHops) { averageHopsValue.set(avgHops); }
    public void updateDeliveryRate(double rate) { deliveryRateValue.set(rate); }
}
