package com.demo.upimesh.observability;

import com.demo.upimesh.protocol.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Audit logging and operational metrics collection.
 */
@Service
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final AtomicLong ingestedCount = new AtomicLong();
    private final AtomicLong settledCount = new AtomicLong();
    private final AtomicLong duplicateCount = new AtomicLong();
    private final AtomicLong invalidCount = new AtomicLong();
    private final Map<String, AtomicLong> stateCounters = new ConcurrentHashMap<>();

    public void recordEvent(String eventType, String packetHash, String details) {
        log.info("[AUDIT-EVENT] type={} hash={} details={}", eventType,
                packetHash != null && packetHash.length() >= 12 ? packetHash.substring(0, 12) + "..." : packetHash,
                details);

        switch (eventType) {
            case "INGESTED" -> ingestedCount.incrementAndGet();
            case "SETTLED" -> settledCount.incrementAndGet();
            case "DUPLICATE_DROPPED" -> duplicateCount.incrementAndGet();
            case "INVALID" -> invalidCount.incrementAndGet();
        }
        stateCounters.computeIfAbsent(eventType, k -> new AtomicLong()).incrementAndGet();
    }

    public Map<String, Object> getMetricsSnapshot() {
        return Map.of(
                "ingestedTotal", ingestedCount.get(),
                "settledTotal", settledCount.get(),
                "duplicateTotal", duplicateCount.get(),
                "invalidTotal", invalidCount.get()
        );
    }
}
