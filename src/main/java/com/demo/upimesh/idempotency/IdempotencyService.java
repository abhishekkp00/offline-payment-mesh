package com.demo.upimesh.idempotency;

import com.demo.upimesh.persistence.Transaction;
import com.demo.upimesh.persistence.TransactionRepository;
import com.demo.upimesh.protocol.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed, durable idempotency service.
 * Layer 1: Redis atomic SET-NX distributed lock with TTL.
 * Layer 2: PostgreSQL UNIQUE constraint on transaction packetHash as final backstop.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    public enum ClaimResult {
        OWNED,       // This instance acquired the processing claim
        DUPLICATE,   // Key already claimed by another process/thread
        UNAVAILABLE  // Redis unavailable & fallback denied claim
    }

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @Value("${upi.mesh.idempotency-ttl-seconds:86400}")
    private long ttlSeconds = 86400;

    @Value("${upi.mesh.processing-lock-ttl-ms:30000}")
    private long processingLockTtlMs = 30000;

    // Fallback atomic state map when Redis connection is not present in local test environments
    private final Map<String, String> localMemoryFallback = new ConcurrentHashMap<>();

    /**
     * Try to claim a packet hash atomically.
     * Uses Redis SET key "PROCESSING" NX PX 30000.
     */
    public ClaimResult claim(String packetHash) {
        String key = "idempotency:" + packetHash;
        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        key,
                        TransactionState.PROCESSING.name(),
                        Duration.ofMillis(processingLockTtlMs)
                );
                if (Boolean.TRUE.equals(acquired)) {
                    return ClaimResult.OWNED;
                } else {
                    return ClaimResult.DUPLICATE;
                }
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for claim ({}), using fallback: {}", packetHash, e.getMessage());
        }

        // In-memory fallback gate for standalone / test environments
        String prev = localMemoryFallback.putIfAbsent(packetHash, TransactionState.PROCESSING.name());
        return prev == null ? ClaimResult.OWNED : ClaimResult.DUPLICATE;
    }

    /**
     * Update the state in Redis after processing step (e.g. VALIDATED or SETTLED).
     */
    public void markState(String packetHash, TransactionState state) {
        String key = "idempotency:" + packetHash;
        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                redisTemplate.opsForValue().set(key, state.name(), Duration.ofSeconds(ttlSeconds));
                return;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for markState: {}", e.getMessage());
        }
        localMemoryFallback.put(packetHash, state.name());
    }

    /**
     * Release/delete claim from Redis if a transient error occurred, allowing retry.
     */
    public void release(String packetHash) {
        String key = "idempotency:" + packetHash;
        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                redisTemplate.delete(key);
                return;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for release: {}", e.getMessage());
        }
        localMemoryFallback.remove(packetHash);
    }

    /**
     * Queries PostgreSQL durable database for existing transaction record.
     */
    public Optional<Transaction> getExistingTransaction(String packetHash) {
        return transactionRepository.findByPacketHash(packetHash);
    }

    public boolean isClaimed(String packetHash) {
        return getExistingTransaction(packetHash).isPresent() || localMemoryFallback.containsKey(packetHash);
    }

    public int size() {
        return (int) transactionRepository.count();
    }

    public void clear() {
        localMemoryFallback.clear();
        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                // optional cleanup for tests
            }
        } catch (Exception ignored) {}
    }
}
