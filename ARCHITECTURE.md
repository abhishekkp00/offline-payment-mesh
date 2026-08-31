# Architecture Specification: Pre-Funded Offline Payment Mesh (DTN)

## Executive Overview
The **Offline Payment Mesh** is a Delay/Disruption Tolerant Network (DTN) payment architecture designed for zero-connectivity and network-partitioned environments. It enables users with pre-funded offline digital wallets to sign and execute payment instructions peer-to-peer (P2P). Intermediate nodes act as store-carry-forward DTN relays, hopping encrypted transaction bundles until an internet-connected bridge node ingests them into the centralized settlement core.

---

## 1. Offline Payment Lifecycle
1. **Creation & Reservation**: The user specifies a recipient, amount, and authorization PIN. The local wallet verifies that the uncommitted offline balance >= transfer amount and reserves the funds.
2. **Cryptographic Signing & Envelope Assembly**: The wallet constructs a `PaymentInstruction` containing `TransactionId`, `WalletId`, `Nonce`, `SignedAt`, and `Amount`. The instruction is signed using the sender's private key and encrypted into a `MeshPacket` using hybrid AES-GCM-256 + RSA-OAEP encryption.
3. **Queue Ingestion**: The resulting bundle is enqueued into the local DTN bundle store with an initial Time-To-Live (TTL).
4. **Peer Discovery & Gossip Hop**: As devices come within proximity, bilateral DTN bundle exchanges transmit copies to peer stores, decrementing TTL.
5. **Gateway Bridge Uplink**: An internet-capable bridge node ingests held bundles to the server ingestion endpoint.
6. **Backend Ingestion & Settlement**: The backend validates protocol versions, checks idempotency caches, verifies digital signatures, and executes atomic ledger settlement.

---

## 2. Wallet Lifecycle
1. **Provisioning**: A wallet is initialized with a `WalletId`, assigned to an account holder, pre-funded with a baseline credit balance, and assigned cryptographic keypairs (`KeyId`).
2. **Active State**: The wallet accepts offline payment instructions, decrements local available balance, and signs payloads.
3. **Synchronization / Lock**: When online, the wallet syncs local transaction history with the central ledger. If fraud or double-spend is detected, the wallet transitions to `SUSPENDED`.

---

## 3. Packet (DTN Bundle) Lifecycle
```
[ SENDER ] ---> (Enqueued: TTL=N) ---> [ STORE-CARRY-FORWARD RELAYS ] ---> (TTL=N-k) ---> [ BRIDGE NODE ]
                                                                                                 |
                                                                                         (POST /api/bridge/ingest)
                                                                                                 v
                                                                                       [ SETTLEMENT ENGINE ]
```
1. **CREATED**: Originating device packages encrypted ciphertext, Outer `PacketId`, `TTL`, `KeyId`, and Timestamp.
2. **RELAYING**: Intermediate nodes store and forward bundle without accessing plaintext (zero-knowledge relaying).
3. **EXPIRED**: If TTL reaches 0 or age exceeds freshness window before reaching a bridge, the packet is purged.
4. **INGESTED**: Bridge node submits bundle to `/api/bridge/ingest`.

---

## 4. Settlement Lifecycle
1. **Idempotency Gate**: Server computes SHA-256 hash of payload ciphertext. An atomic check-and-set claim checks if the hash has already been processed. If claimed, returns `DUPLICATE_DROPPED`.
2. **Signature & Cryptographic Verification**: Decrypts AES key via RSA private key, decrypts payload via AES-GCM, and checks payload freshness against the configured max age window.
3. **Ledger Transaction**: Acquires database transaction, verifies sender pre-funded account balance, executes atomic debit and credit, and updates transaction record to `SETTLED`.

---

## 5. Trust Boundaries
* **Client (Untrusted Relay Environment)**: Peer nodes are assumed hostile. Intermediate devices can eavesdrop, drop, delay, or replay bundles. Zero-knowledge hybrid encryption protects privacy and payload integrity.
* **Bridge Nodes (Semi-Trusted Ingress)**: Bridge nodes submit enqueued bundles to the backend. They cannot modify payload contents due to RSA signatures and AES-GCM authentication tags.
* **Settlement Server (Trusted Core)**: Holds server private keys, maintains master ledger accounts, enforces idempotency, and commits state transitions.

---

## 6. Threat Model & Security Controls
| Threat | Risk Level | Mitigation Control |
| :--- | :--- | :--- |
| **Payload Eavesdropping** | High | AES-GCM-256 symmetric payload encryption. |
| **Relay Tampering / MITM** | High | RSA-2048 Digital Signatures + AES-GCM 128-bit Tag validation. |
| **Replay Attacks** | High | Per-transaction UUID `Nonce` + SHA-256 Idempotency Set + Freshness Window. |
| **Double-Spending** | Critical | Server-side atomic idempotency locks + Database transaction row locking. |
| **Infinite Relay Flooding** | Medium | Bounded Time-To-Live (TTL) decrement per gossip hop. |

---

## 7. Consistency Guarantees
* **Eventual Consistency**: Peer node state is eventually reconciled with the backend when any node reaches internet coverage.
* **At-Most-Once Processing**: Guaranteed by SHA-256 idempotency cache and unique DB index constraints on ciphertext hash.
* **ACID Ledger Mutation**: Account balance adjustments occur within isolation-guaranteed Spring `@Transactional` database boundaries.

---

## 8. Known Limitations
1. **Asynchronous Double-Spend Exposure**: If an attacker splits funds across two isolated offline partitions, the first transaction to reach the server settles, while the second is marked `REJECTED`. Merchant risk is bounded by pre-funded credit limits.
2. **Memory TTL Eviction**: Idempotency keys are cached for a configurable window (e.g. 24h). Submissions beyond the window rely on permanent DB transaction index constraints.
