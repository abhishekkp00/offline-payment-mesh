package com.demo.upimesh.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity mapping cryptographic key metadata.
 */
@Entity
@Table(name = "cryptographic_keys")
public class CryptographicKeyEntity {

    @Id
    @Column(name = "key_id")
    private String keyId;

    @Column(name = "algorithm", nullable = false)
    private String algorithm;

    @Column(name = "key_type", nullable = false)
    private String keyType;

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    public CryptographicKeyEntity() {}

    public CryptographicKeyEntity(String keyId, String algorithm, String keyType, String publicKeyPem) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.keyType = keyType;
        this.publicKeyPem = publicKeyPem;
    }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }

    public String getPublicKeyPem() { return publicKeyPem; }
    public void setPublicKeyPem(String publicKeyPem) { this.publicKeyPem = publicKeyPem; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
