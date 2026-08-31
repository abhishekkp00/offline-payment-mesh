# Offline Double-Spending Detection & Reconciliation Architecture

## Architectural Truth: Cryptography Alone Does NOT Prevent Offline Double-Spending

> **Fundamental Principle**: In a fully disconnected peer-to-peer environment without continuous central ledger communication or tamper-proof hardware enclaves, **cryptographic signatures guarantee authenticity and integrity, NOT uniqueness or non-double-spending**.
>
> If a user receives a signed offline authorization token for ₹5,000, they can present that same valid signed token to two isolated merchants in physically disconnected locations. Both merchants' devices verify valid signatures locally. It is **only when transactions reach the central server at reconciliation time that the double-spend is detected**.

---

## 1. Attack Categorization Matrix

| Attack Vector | Layer Detected | Defense Mechanism | Server Classification Outcome |
| :--- | :--- | :--- | :--- |
| **Signature Forgery** | Intake / Crypto Validation | Asymmetric Ed25519 signature verification against registered device key | `FORGED_TRANSACTION` / `REJECTED` |
| **Replay Attack** | Intake / Nonce Check | SHA-256 Nonce deduplication & 24h timestamp freshness window | `REPLAY_ATTACK` / `EXPIRED` |
| **Duplicate Delivery** | Intake / Idempotency | Redis atomic `SET-NX` ciphertext hash lock + PostgreSQL `UNIQUE` index | `DUPLICATE_TRANSACTION` / `DUPLICATE_DROPPED` |
| **Single Tx Overspend** | Reconciliation Engine | Amount check against `wallet_authorizations.authorized_balance` | `OVERSPENDING` / `OVERSPENT` |
| **Offline Double-Spend** | Reconciliation Engine | Cumulative spend evaluation across distinct offline transactions against same allowance | `CONFLICTING_OFFLINE_TRANSACTIONS` / `CONFLICTED` |

---

## 2. Deterministic Reconciliation Engine Rules

When multiple distinct offline transactions signed against the same wallet allowance reach the backend:

### Policy A: `FIRST_ARRIVED_WINS` (Default Production Policy)
1. Ingestion arrival timestamp determines priority.
2. The transaction that reaches the server gateway first claims available pre-funded wallet balance $\rightarrow$ `SETTLED`.
3. Subsequent conflicting transactions that cause cumulative spend to exceed authorized balance are marked **`CONFLICTED`**.
4. **Economic Allocation**: Merchant 1 receives funds; Merchant 2's payment is rejected as `CONFLICTED`.

### Policy B: `TIMESTAMP_PRIORITY`
1. Transaction with the earlier client `signedAt` timestamp takes priority.
2. Requires verified device clock or trusted hardware timestamping.

---

## 3. Economic Consequences & Liability Allocation

When an offline double-spend occurs across two merchants:

```
                          [ Sender Offline Wallet (Limit: ₹500) ]
                                    /               \
                                   /                 \
                  Payment A: ₹400 /                   \ Payment B: ₹400
                    (Merchant 1)                       (Merchant 2)
                         |                                  |
                         ▼                                  ▼
                 [ Reaches Server #1 ]              [ Reaches Server #2 ]
                 Result: SETTLED                    Result: CONFLICTED
                 Merchant 1 Paid                    Merchant 2 Unpaid
```

1. **Merchant Financial Risk**: Under `FIRST_ARRIVED_WINS`, the merchant whose transaction arrives second suffers a settlement failure.
2. **Sender Overdraft Recovery**: If the network policy allows settlement despite overdraft, the sender's online bank account is placed in negative balance/overdraft, and recovery procedures are triggered.
3. **Network Guarantee Fund**: Production networks (e.g. NPCI UPI Lite) mandate small pre-funded wallet caps (e.g. ₹2,000 max wallet, ₹500 per transaction limit) backed by a central liquidity guarantee pool.

---

## 4. Hardware Enclave & Banking Requirements for Absolute Non-Double-Spending

To eliminate offline double-spending entirely, client hardware must implement **tamper-proof hardware-enforced counters**:

### A. Secure Execution Environments (TEE / SE)
* **Hardware Enclaves**: ARM TrustZone, Apple Secure Enclave, or JavaCard SIM eSE (Embedded Secure Element).
* **Monotonic Counters**: The private signing key resides inside the Secure Element. The enclave maintains a **hardware monotonic counter** that increments with every signature.
* **On-Device Double-Spend Blocking**: The SE refuses to sign a new transaction if local available balance is insufficient, preventing the client software from generating double-spend signatures even on a rooted device.

### B. Banking Infrastructure (UPI Lite Architecture)
* **Pre-Funded Escrow Account**: On-device wallet funds are locked in an online escrow bank account at issuance time.
* **Hardware Token (`LITE_NC`)**: The server issues a hardware-locked counter token verified by the device's Secure Element.
