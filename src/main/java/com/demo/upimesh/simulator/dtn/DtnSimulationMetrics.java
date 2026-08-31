package com.demo.upimesh.simulator.dtn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks DTN routing and simulation metrics.
 */
public class DtnSimulationMetrics {

    private final AtomicInteger packetsGenerated = new AtomicInteger();
    private final AtomicInteger packetsDelivered = new AtomicInteger();
    private final AtomicInteger packetDrops = new AtomicInteger();
    private final AtomicInteger packetCopies = new AtomicInteger();
    private final AtomicInteger duplicateTransmissions = new AtomicInteger();

    private final AtomicLong totalDeliveryLatencyMs = new AtomicLong();
    private final AtomicLong totalHopCount = new AtomicLong();

    private final Map<String, Double> nodeBufferUtilization = new ConcurrentHashMap<>();

    public void recordPacketGenerated() {
        packetsGenerated.incrementAndGet();
    }

    public void recordPacketDelivered(long latencyMs, int hops) {
        packetsDelivered.incrementAndGet();
        totalDeliveryLatencyMs.addAndGet(latencyMs);
        totalHopCount.addAndGet(hops);
    }

    public void recordPacketDrop() {
        packetDrops.incrementAndGet();
    }

    public void recordPacketCopy() {
        packetCopies.incrementAndGet();
    }

    public void recordDuplicateTransmission() {
        duplicateTransmissions.incrementAndGet();
    }

    public void updateBufferUtilization(String nodeId, double utilizationPercentage) {
        nodeBufferUtilization.put(nodeId, utilizationPercentage);
    }

    public double getDeliveryProbability() {
        int gen = packetsGenerated.get();
        if (gen == 0) return 0.0;
        return ((double) packetsDelivered.get() / gen) * 100.0;
    }

    public double getAverageLatencyMs() {
        int del = packetsDelivered.get();
        if (del == 0) return 0.0;
        return (double) totalDeliveryLatencyMs.get() / del;
    }

    public double getAverageHopCount() {
        int del = packetsDelivered.get();
        if (del == 0) return 0.0;
        return (double) totalHopCount.get() / del;
    }

    public double getMaxBufferUtilization() {
        return nodeBufferUtilization.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    public Map<String, Object> getMetricsSummary() {
        return Map.of(
                "packetsGenerated", packetsGenerated.get(),
                "packetsDelivered", packetsDelivered.get(),
                "packetDrops", packetDrops.get(),
                "packetCopies", packetCopies.get(),
                "duplicateTransmissions", duplicateTransmissions.get(),
                "deliveryProbabilityPercentage", Math.round(getDeliveryProbability() * 100.0) / 100.0,
                "averageLatencyMs", Math.round(getAverageLatencyMs() * 100.0) / 100.0,
                "averageHopCount", Math.round(getAverageHopCount() * 100.0) / 100.0,
                "maxBufferUtilizationPercentage", Math.round(getMaxBufferUtilization() * 100.0) / 100.0
        );
    }
}
