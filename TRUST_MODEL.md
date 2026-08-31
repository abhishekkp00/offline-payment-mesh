# Trust Model & Cryptographic Key Lifecycle Specification

## Executive Summary
This document specifies the end-to-end cryptographic trust model, device & gateway bridge identities, key rotation lifecycle, and production Hardware Security Module (HSM) / KMS mapping for the **Offline Payment Mesh**.

---

## 1. Cryptographic Identity Model

### A. Client Device Identity (`Device.java`)
Every hardware mobile client has a unique cryptographic identity:
* **`deviceId`**: Unique client identifier string (e.g. `dev-alice-001`).
* **`publicKeyBase64`**: Client Ed25519 Public Key encoded in Base64 (X.509 format).
* **`status`**: State machine (`ACTIVE`, `REVOKED`, `EXPIRED`).
* **Signature Guarantee**: Every `PaymentInstruction` payload signed offline by the client device MUST be verified against `publicKeyBase64`. Non-repudiation is cryptographically enforced.

### B. Gateway Bridge Identity (`BridgeNodeEntity.java`)
Every internet-connected DTN gateway bridge node has a registered identity:
* **`nodeId`**: Unique bridge node identifier (e.g. `phone-bridge`, `bridge-1`).
* **`status`**: State machine (`ACTIVE`, `REVOKED`, `EXPIRED`).
* **Authentication**: Ingestion requests (`POST /api/bridge/ingest`) verify bridge identity via `BridgeTrustService.validateBridgeTrust(bridgeId)`. Uploads from unregistered or revoked bridge nodes are rejected (`unauthorized_bridge`).

---

## 2. Server Key Management & Persistent Security

### Development Storage (`ServerKeyHolder.java`)
* Server RSA-2048 encryption keys and Ed25519 authorization signing keys are saved to and loaded from local persistent key storage (`.keys/` directory).
* Keys persist across backend application restarts.
* Private key files (`.keys/server_rsa_private.key`, `.keys/server_ed25519_private.key`) and certificates are excluded from version control via `.gitignore`.

### Production Mapping: AWS KMS / GCP Cloud KMS / HashiCorp Vault / HSM
In production environments:
1. **Server Asymmetric Private Keys**: Managed inside FIPS 140-2 Level 3 Hardware Security Modules (HSMs) or AWS KMS / GCP KMS / HashiCorp Vault Transit engine.
2. **Key Encapsulation & Signing**: Server private key operations (`RSA-OAEP` session key unwrapping and `Ed25519` token signing) execute inside HSM memory boundaries. Private keys never leave the secure enclave.

---

## 3. Cryptographic Key Lifecycle & Key Rotation (`KeyLifecycleService.java`)

```
   [ ACTIVE ] ──(Rotate)──► [ ROTATED ] ──(Expire/Revoke)──► [ EXPIRED / REVOKED ]
        ▲                         │                                    │
        │                         ▼                                    ▼
   Current Keys           Valid for verification,               Rejected upon
   for Encryption        new tokens use new KeyId              ingestion attempt
```

### Key Rotation Lifecycle
1. **Registration**: New key `keyId` registered with status `ACTIVE`.
2. **Rotation**: When a key is rotated:
   - Previous key status transitions to `ROTATED`.
   - New key `newKeyId` becomes `ACTIVE`.
   - During the transition window, `ROTATED` keys remain valid for verification of in-flight offline bundles, allowing zero-downtime key rotation.
3. **Revocation & Expiration**:
   - `REVOKED` or `EXPIRED` keys immediately fail `keyLifecycleService.validateKeyStatus(keyId)`.
   - Bundles referencing revoked/expired keys are rejected (`INVALID`).

---

## 4. Production Mutual TLS (mTLS) Architecture

For production bridge-to-server communication:
* **mTLS Ingress**: NGINX / Cloudflare Gateway terminates client TLS connections requiring mutual TLS (`ssl_verify_client on`).
* **X.509 Certificate Verification**: Bridge nodes present X.509 client certificates issued by the Payment Network Certificate Authority (CA).
* **Sanitize Headers**: The reverse proxy passes validated `X-Client-Cert-DN` and `X-Bridge-Node-Id` headers to the Spring Boot ingestion service.

---

## 5. Security Test Matrix (`TrustModelSecurityTest.java`)

| Test Case | Scenario | Expected Outcome |
| :--- | :--- | :--- |
| `testValidDevicePayment` | Active registered device signs offline payment | `SETTLED` (PASS) |
| `testRevokedDevicePayment` | Revoked hardware device signs offline payment | `INVALID: REVOKED` (REJECTED) |
| `testUnknownDevicePayment` | Unregistered device attempts payment | `INVALID: Unknown hardware device` (REJECTED) |
| `testRotatedKeyPayment` | Payment verification during active key rotation | `SETTLED` (PASS) |
| `testExpiredKeyPayment` | Payment references expired key ID | `INVALID: EXPIRED` (REJECTED) |
| `testForgedSignaturePayment` | Attacker tampers device signature | `INVALID: Invalid device signature` (REJECTED) |
| `testUnauthorizedBridgePayment` | Unregistered or revoked bridge uploads bundle | `INVALID: unauthorized_bridge` (REJECTED) |
