# Architecture Specification: Pre-Funded Offline Payment Mesh (DTN)

## Executive Overview
The **Offline Payment Mesh** is a Delay/Disruption Tolerant Network (DTN) payment architecture designed for zero-connectivity and network-partitioned environments. It enables users with pre-funded offline digital wallets to sign and execute payment instructions peer-to-peer (P2P). Intermediate nodes act as store-carry-forward DTN relays, hopping encrypted transaction bundles until an internet-connected bridge node ingests them into the centralized settlement core.

---

## 1. Offline Payment Lifecycle

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
6. **Encrypted Bundle Enqueuing**: Instruction is sealed into an encrypted `MeshPacket` (AES-256-GCM + RSA-2048) and enqueued into local DTN store. No network connection is required.

### ONLINE Reconciliation Phase
1. **Idempotency Check**: Server computes SHA-256 hash of payload ciphertext and performs atomic claim in idempotency cache.
2. **Payload Decryption**: Server decrypts payload using Server RSA Private Key and AES-256-GCM key.
3. **Server Authorization Verification**: Server verifies `serverSignature` using Server Ed25519 Public Key.
4. **Device Transaction Verification**: Server verifies `deviceSignature` using Device Ed25519 Public Key (extracted from `WalletAuthorization`).
5. **Freshness & Expiry Bounds**: Server verifies timestamp age $\le \text{maxAgeSeconds}$ and `expiry >= signedAt`.
6. **Allowance Verification**: Server verifies amount $\le \text{authorizedBalance}$.
7. **Atomic Settlement**: Server executes debit and credit in bank database and updates transaction state to `SETTLED`.

---

## 2. Cryptographic Architecture & Primitive Separation

| Component | Cryptographic Primitive | Key Identity | Purpose |
| :--- | :--- | :--- | :--- |
| **Server Identity & Authorization** | Ed25519 Signature | `ServerKeyHolder` Ed25519 Key | Authenticates pre-funded wallet issuance and spending limit. |
| **Device Identity & Non-Repudiation** | Ed25519 Signature | Device Ed25519 KeyPair | Authenticates transaction intent; prevents forgery/tampering. |
| **Payload Data Privacy** | AES-256-GCM | Ephemeral 256-bit Session Key | Authenticated encryption for confidential payload data. |
| **Key Encapsulation** | RSA-2048 / OAEP-SHA256 | Server RSA Public Key | Secures symmetric AES session key for server decryption. |
| **Deduplication / Idempotency** | SHA-256 Hash | Hex Ciphertext Digest | Prevents duplicate processing across multiple bridge uploads. |

---

## 3. Security Guarantees & Tampering Protection

1. **Amount Tamper Protection**: Any alteration of `amount` invalidates the `deviceSignature` verification check (`ed25519Service.verify`).
2. **Receiver Tamper Protection**: Any alteration of `receiverVpa` invalidates `deviceSignature`.
3. **Sender / Wallet ID Protection**: Any alteration of `senderVpa` or `walletId` invalidates `deviceSignature` or `serverSignature`.
4. **Nonce / Replay Protection**: Each transaction carries a client UUID `nonce` + SHA-256 ciphertext hash. Replayed bundles are dropped as `DUPLICATE_DROPPED`.
5. **Non-Repudiation**: Senders cannot deny payment authorization because transactions require a signature from their private key.

---

## 4. Remaining Offline Double-Spend Attacks & Mitigations

### The Partitioned Offline Double-Spend Attack
* **Scenario**: An attacker clones local wallet state or uses two isolated physical sub-meshes under zero connectivity to issue two separate payments (e.g. ₹400 to Bob in Sub-mesh A, and ₹400 to Carol in Sub-mesh B) against a single ₹500 pre-funded authorization token.
* **Reconciliation Behavior**:
  - Whichever packet reaches an internet-connected Gateway first (e.g. Sub-mesh A) acquires the database lock and settles successfully (₹400 transferred).
  - When the second packet reaches the Gateway (Sub-mesh B), the server detects that total spent exceeds master account balance or allowance, marking the second transaction `REJECTED`.

### Risk Bounding Controls
1. **Pre-Funded Spending Caps**: Bounded max allowance per token (e.g. max ₹500 per authorization).
2. **Short Expiry Windows**: Authorizations expire after a configurable TTL (e.g. 24 hours).
3. **Hardware Enclave Key Storage**: Production mobile deployments store device private keys inside Android Keystore / iOS Secure Enclave to prevent wallet state cloning.
