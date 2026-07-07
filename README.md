# UPI Offline Mesh: Decentralized Asynchronous Settlement Protocol

A Spring Boot backend demonstrating a **deferred-settlement payment protocol** routed through a simulated offline Bluetooth-style mesh network. This protocol enables end-to-end secure transaction routing through untrusted peer-to-peer (P2P) nodes under zero-connectivity environments.

---

## 1. System Architecture

The protocol separates the offline transaction generation from the online backend settlement. 

```mermaid
sequenceDiagram
    autonumber
    actor Sender as Sender (Offline)
    participant Peer as Peer Node (Offline)
    participant Bridge as Bridge Node (Internet Enabled)
    participant Backend as Spring Boot Backend
    database DB as H2 Database

    Note over Sender: 1. Encrypts PaymentInstruction<br/>(RSA-OAEP + AES-256-GCM)
    Sender->>Peer: Broadcast over Mesh (MeshPacket)
    Note over Peer: Decrements TTL (Gossip Round)
    Peer->>Bridge: Broadcast over Mesh (MeshPacket)
    Note over Bridge: Detects Internet Connectivity
    Bridge->>Backend: HTTPS POST /api/bridge/ingest
    Note over Backend: 2. SHA-256 Ciphertext Hashing<br/>3. Atomic Idempotency Check<br/>4. Decrypt & Authenticate Tag<br/>5. Freshness Check (24h Window)
    Backend->>DB: 6. Atomic Debit/Credit Settlement (Optimistic Lock)
    DB-->>Backend: Confirmed
    Backend-->>Bridge: Response: SETTLED
```

---

## 2. Core Architectural Problems & Solutions

### A. Untrusted Intermediaries (Confidentiality & Integrity)
Because transaction packets hop across unknown peer devices, the payload must remain completely confidential and tamper-proof.
*   **Solution**: **Hybrid Cryptography (RSA-OAEP + AES-256-GCM)**.
    1. The sender generates an ephemeral, transaction-specific symmetric **AES-256** key.
    2. The sender encrypts the JSON transaction payload with **AES-GCM**, which provides authenticated encryption.
    3. The sender encrypts the ephemeral AES key with the server's public **RSA-2048** key.
    4. The packet structure is packed as: `[RSA-encrypted AES key (256 bytes)] + [IV (12 bytes)] + [AES ciphertext + GCM Tag (16 bytes)]`.
*   **Defense**: The **GCM Tag** guarantees integrity. If an intermediary attempts to tamper with a single bit of the transit payload, the GCM tag authentication fails during decryption, and the packet is immediately discarded.

### B. Duplicate Storms (Distributed Idempotency)
When multiple offline bridge nodes obtain the same transaction packet and regain connectivity simultaneously, they will all attempt to upload it to the server. Without protection, this would cause multiple debits for a single payment.
*   **Solution**: **Atomic Ciphertext Hashing**.
    1. The backend calculates `SHA-256(ciphertext)` immediately upon intake.
    2. It attempts to claim the hash atomically:
       ```java
       Instant prev = seen.putIfAbsent(packetHash, now);
       return prev == null; // True = first claimer (processes transaction); False = duplicate (dropped)
       ```
    3. In-memory `ConcurrentHashMap` (which translates to `SETNX` in a production Redis configuration) guarantees that only the first thread processes the payment; the rest are short-circuited.
*   **Defense-in-depth**: The database schema enforces a `unique` constraint on the transaction's `packet_hash` column to prevent duplicate settlement at the storage layer.

### C. Replay Attacks
Attackers capturing a valid ciphertext off the air could attempt to replay it at a later date to repeatedly drain a sender's account.
*   **Solution**: **Double-Key Verification**.
    1. **Dynamic Nonce**: Every payment instruction contains a unique UUID nonce, meaning two legitimate identical payments (same sender, receiver, and amount) yield distinct ciphertexts and hashes.
    2. **Freshness Window**: Every payload contains an encrypted timestamp (`signedAt`). The server rejects packets signed more than 24 hours prior to ingest, neutralizing long-term replay threats.

---

## 3. Directory Map

```
upi-offline-mesh/
├── pom.xml                                  # Dependencies (Spring Boot 3.3, JPA, H2)
└── src/
    ├── main/
    │   ├── resources/
    │   │   ├── application.properties       # DB, logging, and TTL properties
    │   │   └── templates/dashboard.html     # Real-time simulation dashboard
    │   └── java/com/demo/upimesh/
    │       ├── UpiMeshApplication.java      # Application bootstrap
    │       ├── config/
    │       │   └── AppConfig.java           # Scheduled eviction configuration
    │       ├── controller/
    │       │   ├── ApiController.java       # Mesh management and ingestion endpoints
    │       │   └── DashboardController.java  # Serves MVC templates
    │       ├── crypto/
    │       │   ├── ServerKeyHolder.java     # RSA KeyPair manager
    │       │   └── HybridCryptoService.java # AES-GCM and RSA crypto algorithms
    │       ├── model/
    │       │   ├── Account.java             # JPA Entity with Optimistic Locking versioning
    │       │   ├── Transaction.java         # Settlement Ledger entity
    │       │   ├── MeshPacket.java          # Transit data envelope (ciphertext wrapper)
    │       │   └── PaymentInstruction.java  # Decrypted transaction payload
    │       └── service/
    │           ├── BridgeIngestionService.java # Pipeline coordinator (Ingest -> Hash -> Decrypt -> Settle)
    │           ├── IdempotencyService.java  # Thread-safe JVM deduplication cache
    │           ├── SettlementService.java   # Transactional balance settlement logic
    │           ├── MeshSimulatorService.java# Gossip protocol and device range emulator
    │           └── VirtualDevice.java       # Simulated mesh node definition
    └── test/
        └── java/com/demo/upimesh/
            └── IdempotencyConcurrencyTest.java # Integrations tests (tampering & concurrent threads)
```

---

## 4. API Endpoints

### Core Ingestion Interface
*   **Endpoint**: `POST /api/bridge/ingest`
*   **Headers**: 
    *   `X-Bridge-Node-Id`: Identifier of the reporting bridge node.
    *   `X-Hop-Count`: Number of hops traversed in the mesh.
*   **Payload**:
    ```json
    {
      "packetId": "550e8400-e29b-41d4-a716-446655440000",
      "ttl": 3,
      "createdAt": 1730000000000,
      "ciphertext": "base64-encoded-RSA-and-AES-envelope-data..."
    }
    ```
*   **Response**:
    ```json
    {
      "outcome": "SETTLED", // Options: SETTLED, DUPLICATE_DROPPED, INVALID
      "packetHash": "8f3b145ac...",
      "reason": null,
      "transactionId": 102
    }
    ```

### Simulation & Control Endpoints
| Verb | Route | Description |
|---|---|---|
| `GET` | `/` | Renders the simulated dashboard interface |
| `GET` | `/api/server-key` | Retrieves the server's public RSA key (Base64) |
| `GET` | `/api/accounts` | Lists mock account balances |
| `GET` | `/api/transactions` | Lists historical settled transactions |
| `GET` | `/api/mesh/state` | Returns the state of all nodes in the simulated mesh |
| `POST` | `/api/demo/send` | Generates and injects a payment into the mesh |
| `POST` | `/api/mesh/gossip` | Runs one tick of the simulated epidemic routing gossip |
| `POST` | `/api/mesh/flush` | Commands active bridge nodes to upload their payloads |
| `POST` | `/api/mesh/reset` | Resets all balances, clears the mesh, and flushes the deduplication cache |

---

## 5. Production Readiness & Limitations

This codebase serves as an architectural prototype. Transitioning to production requires addressing the following areas:

| Current Architecture | Production Standard |
|---|---|
| **H2 In-Memory Storage** | PostgreSQL or CockroachDB with read-replicas |
| **Local Cache Deduplication** | Distributed Redis deployment utilizing atomic `SET NX EX` keys |
| **Simulated Gossip** | Bluetooth Low Energy (BLE) or Wi-Fi Direct protocols on client devices |
| **Ephemeral Key Generation** | HSM (Hardware Security Module) management of the server private key |
| **Unauthenticated Ingest** | Mutual TLS (mTLS) or signed Client Certificates for bridge validation |

### Fundamental Protocol Trade-Offs

1.  **Trust-on-First-Sight Balance Risk**: In a fully offline environment, the receiver's device cannot verify the sender's actual account balance. If the sender's account is depleted when the packet is uploaded, the transaction fails settlement at the backend. *This is why real-world production frameworks (e.g., UPI Lite) use pre-funded hardware-locked on-device wallets.*
2.  **Offline Double-Spending**: A malicious user could issue the same offline balance to multiple sellers in isolated physical environments. Settlement is race-condition controlled at the server (first to upload wins), but the loser suffers a loss.
