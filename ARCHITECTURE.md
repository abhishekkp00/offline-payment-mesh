# Architecture Specification: Pre-Funded Offline Payment Mesh (DTN)

## Executive Overview
The **Offline Payment Mesh** is a Delay/Disruption Tolerant Network (DTN) payment architecture designed for zero-connectivity and network-partitioned environments. It enables users with pre-funded offline digital wallets to sign and execute payment instructions peer-to-peer (P2P). Intermediate nodes act as store-carry-forward DTN relays, hopping encrypted transaction bundles until an internet-connected bridge node ingests them into the centralized settlement core.

---

## 1. Packet Format & GCM Additional Authenticated Data (AAD) Binding

```
+---------------------------------------------------------------------------------------------------+
|                                  DTN MESH PACKET (OVER-THE-WIRE)                                  |
+---------------------------------------------------------------------------------------------------+
| UNENCRYPTED ROUTING METADATA (Readable by DTN Relays):                                            |
|   • version          : "1.0.0"                                                                    |
|   • packetId         : "pkt-uuid-1234"                                                            |
|   • transactionId    : "tx-uuid-5678"                                                             |
|   • walletId         : "wlet-alice-demo"                                                          |
|   • keyId            : "key-server-rsa-2048"                                                      |
|   • originDeviceId   : "phone-alice"                                                              |
|   • ttl / hopCount   : 5 / 0                                                                      |
|   • createdAt        : 1788168000000                                                              |
+---------------------------------------------------------------------------------------------------+
|                                              ||                                                   |
|                        Canonical Serialization (AAD Bytes)                                        |
|         version | packetId | transactionId | walletId | createdAt                             |
|                                              ||                                                   |
|                                              v                                                    |
| ENCRYPTED PAYLOAD (AES-256-GCM Authenticated Encryption):                                         |
|   • RSA-OAEP Encrypted Session Key (256 bytes)                                                    |
|   • AES-GCM Initialization Vector (12 bytes)                                                      |
|   • Encrypted JSON Payload (PaymentInstruction + WalletAuthorization + Device Signature)          |
|   • AES-GCM Authentication Tag (16 bytes) <--- Cryptographically binds AAD Bytes!                  |
+---------------------------------------------------------------------------------------------------+
```

---

## 2. Cryptographic Flow & Dual Signature Validation

```
[ OFFLINE SENDER DEVICE ]                                               [ SERVER INGESTION CORE ]
  1. Local Server Token Verification                                      1. Check KeyId == "key-server-rsa-2048"
     verify(ServerSignature, ServerPublicKey)                             2. Check Packet Size <= 64 KB
  2. Reserve Offline Balance                                              3. Compute Canonical AAD Bytes
  3. Sign Tx Payload with Device Ed25519 Key                                 version|packetId|txId|walletId|createdAt
  4. Compute Canonical AAD Bytes                                          4. Decrypt AES Key via RSA Private Key
     version|packetId|txId|walletId|createdAt                             5. Decrypt AES-GCM Ciphertext with AAD
  5. Encrypt Payload with AES-256-GCM + AAD                                 (AEADBadTagException if AAD tampered!)
  6. Enqueue in DTN Store                                                 6. Verify WalletAuthorization Server Sig
                                                                          7. Verify PaymentInstruction Device Sig
                                                                          8. Check Expiry & Max Age Windows
                                                                          9. Atomic Ledger Settlement
```

---

## 3. Offline Payment Lifecycle

### ONLINE Issuance Phase
1. **Authentication**: Client authenticates with the Central Core.
2. **Reservation**: Server reserves an authorized offline spending allowance (e.g., ₹500.00) from the user's master account balance.
3. **WalletAuthorization Token Issuance**: Server constructs `WalletAuthorization` containing:
   - `version` ("1.0.0")
   - `walletId` (e.g., `wlet-alice-demo`)
   - `accountVpa` (`alice@demo`)
   - `deviceId` (`dev-alice-001`)
   - `devicePublicKeyBase64` (Device Ed25519 Public Key)
   - `authorizedBalance` (₹500.00)
   - `issuedAt` & `expiry` (e.g., valid for 24h)
   - `serverNonce` & `serverKeyId`
4. **Server Ed25519 Signature**: Server signs canonical token bytes using its Server Ed25519 Private Key and returns token to the wallet.

### OFFLINE Payment Creation Phase
1. **Token Local Verification**: Client verifies `WalletAuthorization` server signature using Server Ed25519 Public Key.
2. **Balance & Expiry Check**: Client checks `expiry > currentTime` and `availableBalance >= amount`.
3. **Canonical Payload Construction**: Client creates `PaymentInstruction` containing `TransactionId`, `senderVpa`, `receiverVpa`, `amount`, `clientNonce`, and `signedAt`.
4. **Device Ed25519 Signing**: Client signs canonical transaction bytes using its Device Ed25519 Private Key.
5. **Local Balance Reduction**: Client reserves/deducts `amount` from local available balance.
6. **Encrypted Bundle Enqueuing**: Instruction is sealed into an encrypted `MeshPacket` (AES-256-GCM + RSA-2048 + AAD) and enqueued into local DTN store. No network connection is required.

### ONLINE Reconciliation Phase
1. **Idempotency Check**: Server computes SHA-256 hash of payload ciphertext and performs atomic claim in idempotency cache.
2. **Payload Decryption & AAD Verification**: Server decrypts payload using Server RSA Private Key and AES-256-GCM key with `packet.getCanonicalAad()`.
3. **Server Authorization Verification**: Server verifies `serverSignature` using Server Ed25519 Public Key.
4. **Device Transaction Verification**: Server verifies `deviceSignature` using Device Ed25519 Public Key (extracted from `WalletAuthorization`).
5. **Freshness & Expiry Bounds**: Server verifies timestamp age $\le \text{maxAgeSeconds}$ and `expiry >= signedAt`.
6. **Allowance Verification**: Server verifies amount $\le \text{authorizedBalance}$.
7. **Atomic Settlement**: Server executes debit and credit in bank database and updates transaction state to `SETTLED`.

---

## 4. Cryptographic Architecture & Primitive Separation

| Component | Cryptographic Primitive | Key Identity | Purpose |
| :--- | :--- | :--- | :--- |
| **Server Identity & Authorization** | Ed25519 Signature | `ServerKeyHolder` Ed25519 Key | Authenticates pre-funded wallet issuance and spending limit. |
| **Device Identity & Non-Repudiation** | Ed25519 Signature | Device Ed25519 KeyPair | Authenticates transaction intent; prevents forgery/tampering. |
| **Payload Data Privacy & AAD Binding** | AES-256-GCM + AAD | Ephemeral 256-bit Session Key | Authenticated payload encryption + AAD routing metadata binding. |
| **Key Encapsulation** | RSA-2048 / OAEP-SHA256 | Server RSA Public Key | Secures symmetric AES session key for server decryption. |
| **Deduplication / Idempotency** | SHA-256 Hash | Hex Ciphertext Digest | Prevents duplicate processing across multiple bridge uploads. |

---

## 5. Security Guarantees & Tampering Protection

1. **Routing Metadata Tamper Protection**: Any alteration of unencrypted routing fields (`protocolVersion`, `packetId`, `transactionId`, `walletId`, `createdAt`) causes AES-GCM decryption tag verification failure (`AEADBadTagException`).
2. **Key ID & Algorithm Confusion Protection**: Server enforces `keyId == "key-server-rsa-2048"`, rejecting untrusted algorithm choices.
3. **Maximum Packet Size Bound**: Server rejects payloads exceeding 64 KB (`MAX_PACKET_SIZE_BYTES = 65536`) to prevent memory exhaustion DoS.
4. **Amount & Receiver Tamper Protection**: Any alteration of `amount` or `receiverVpa` invalidates `deviceSignature`.
5. **Nonce & Replay Protection**: Unique `clientNonce` + SHA-256 ciphertext hash. Replayed bundles are dropped as `DUPLICATE_DROPPED`.

---

## 6. Remaining Offline Double-Spend Attacks & Mitigations

### The Partitioned Offline Double-Spend Attack
* **Scenario**: An attacker clones local wallet state or uses two isolated physical sub-meshes under zero connectivity to issue two separate payments against a single pre-funded authorization token.
* **Reconciliation Behavior**:
  - Whichever packet reaches an internet-connected Gateway first acquires the database lock and settles successfully.
  - When the second packet reaches the Gateway, the server detects that total spent exceeds master account balance or allowance, marking the second transaction `REJECTED`.

### Risk Bounding Controls
1. **Pre-Funded Spending Caps**: Bounded max allowance per token (e.g. max ₹500 per authorization).
2. **Short Expiry Windows**: Authorizations expire after a configurable TTL (e.g. 24 hours).
3. **Hardware Enclave Key Storage**: Production mobile deployments store device private keys inside Android Keystore / iOS Secure Enclave to prevent wallet state cloning.
