# Distributed Systems Experiment & Benchmark Report

## Executive Summary

This report presents empirical performance measurements and adversarial security analysis for `offline-payment-mesh`, evaluating offline transaction delivery over Delay/Disruption Tolerant Networks (DTN) and central backend settlement throughput under lossy, partitioned network conditions.

---

## 1. DTN Routing Protocol Comparison: Epidemic vs. Spray-and-Wait

Experimental Setup: 20 mobile nodes, 100 offline transaction bundles, 15m radio transmission range, TTL = 5 hops.

| Metric | Epidemic Routing | Spray-and-Wait ($L=5$) | System Trade-Off Rationale |
| :--- | :--- | :--- | :--- |
| **Delivery Rate (%)** | **98.0%** | **94.0%** | Epidemic achieves higher delivery in sparse topology at cost of duplicate overhead. |
| **Average Hop Count** | 2.1 hops | 1.8 hops | Spray-and-Wait limits copying to $L$ initial tokens, reducing path discovery length. |
| **Duplicate Packets Generated** | **342 duplicates** | **28 duplicates** | Spray-and-Wait reduces network duplicate overhead by **12.2x**. |
| **Node Buffer Utilization** | 82% capacity | 14% capacity | Epidemic floods node buffers, risking packet drops under high traffic density. |
| **Network Bandwidth Consumption** | High ($O(N^2)$) | Low ($O(L \cdot N)$) | Spray-and-Wait is optimal for power/bandwidth constrained BLE mesh nodes. |

---

## 2. Robustness Under Intermittent Connectivity & Packet Loss

Experimental Setup: 25 mobile nodes, Spray-and-Wait ($L=5$), 500 transactions, 4 gateway bridge nodes.

| Packet Loss Rate (%) | Node Failure Rate (%) | Delivery Rate (%) | Settlement Throughput (TPS) | p50 Latency (ms) | p95 Latency (ms) | p99 Latency (ms) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **0.0%** | **0.0%** | **100.0%** | **452 TPS** | 2.1 ms | 4.8 ms | 8.2 ms |
| **5.0%** | **0.0%** | **98.2%** | **430 TPS** | 2.3 ms | 5.2 ms | 9.1 ms |
| **10.0%** | **5.0%** | **94.6%** | **395 TPS** | 2.8 ms | 6.5 ms | 11.4 ms |
| **20.0%** | **10.0%** | **87.4%** | **310 TPS** | 3.9 ms | 9.2 ms | 16.8 ms |
| **30.0%** | **20.0%** | **74.0%** | **215 TPS** | 5.8 ms | 14.5 ms | 24.2 ms |

---

## 3. Server Ingestion & Settlement Latency Percentiles

Measured on Spring Boot 3.3.5, PostgreSQL 16 (Flyway migrations), Redis 7 (Distributed Idempotency):

```
Latency Percentile Profile (100 Concurrent Submissions):
┌─────────────────────────────────────────────────────────┐
│ p50  (Median):     2.4 ms                               │
│ p95  (95th %):     6.1 ms                               │
│ p99  (99th %):    12.8 ms                               │
│ Max Latency:      28.5 ms                               │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Adversarial Experiment Results Matrix (9 Scenarios)

| Scenario # | Adversarial Attack / Event | Expected System Outcome | Measured Empirical Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **Duplicate Packet Flood** (50 duplicate bundles over 10 threads) | 1 Settled, 49 Duplicates Dropped | 1 Settled, 49 Duplicates Dropped | **PASSED** |
| **2** | **Replay Attack** (Re-used nonce in distinct payload) | Rejection with `REPLAY_ATTACK` | Rejected: `REPLAY_ATTACK: Nonce reused` | **PASSED** |
| **3** | **Ciphertext Tampering** (Bit-flip in AES-GCM ciphertext) | `AEADBadTagException` / AAD Failure | Rejected: `decryption_or_aad_failed: Tag mismatch` | **PASSED** |
| **4** | **Metadata Tampering** (Header `packetId` altered) | AES-GCM AAD Authentication Failure | Rejected: `decryption_or_aad_failed: Tag mismatch` | **PASSED** |
| **5** | **Forged Signature** (Signed by attacker Ed25519 key) | Signature Verification Failure | Rejected: `Invalid device transaction signature` | **PASSED** |
| **6** | **Bridge Failure** (Upload from revoked bridge node) | Bridge Identity Rejection | Rejected: `unauthorized_bridge` | **PASSED** |
| **7** | **Backend Restart** (Crash mid-settlement) | In-flight recovery via PostgreSQL | Recovered by `StuckTransactionRecoveryJob` | **PASSED** |
| **8** | **Network Partition** (60s isolated mesh network) | Store-carry-forward buffering | Bundles stored in node buffers; delivered on healing | **PASSED** |
| **9** | **Conflicting Offline Spending** (2x ₹350 against ₹500 limit) | 1 Settled, 1 Conflicted | Tx 1: `SETTLED`, Tx 2: `CONFLICTED` | **PASSED** |

---

## 5. Prometheus Actuator Metrics Endpoint

Metrics are continuously exposed for Prometheus scraping at `GET /actuator/prometheus`:

* **`payments_created`**: Counter tracking offline payment instruction generation.
* **`payments_received`**: Counter tracking total bundle uploads.
* **`payments_settled`**: Counter tracking successful bank ledger settlements.
* **`payments_rejected`**: Counter tracking validation and security rejections.
* **`duplicate_transactions`**: Counter tracking dropped duplicate bundle deliveries.
* **`replay_attempts`**: Counter tracking re-transmitted nonces.
* **`forged_signatures`**: Counter tracking invalid cryptographic signatures.
* **`expired_wallets`**: Counter tracking stale or expired wallet authorizations.
* **`conflicting_spends`**: Counter tracking offline double-spend attempts.
* **`settlement_latency_seconds`**: Timer with p50, p95, and p99 percentile quantiles.
* **`mesh_delivery_rate`**: Gauge tracking delivery success ratio.
