package com.demo.upimesh.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity mapping server-signed wallet authorizations.
 */
@Entity
@Table(name = "wallet_authorizations")
public class WalletAuthorizationEntity {

    @Id
    @Column(name = "wallet_id")
    private String walletId;

    @Column(name = "account_vpa", nullable = false)
    private String accountVpa;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "authorized_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal authorizedBalance;

    @Column(name = "max_per_tx_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxPerTxAmount;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "server_nonce", nullable = false)
    private String serverNonce;

    @Column(name = "server_key_id", nullable = false)
    private String serverKeyId;

    @Column(name = "server_signature", nullable = false, columnDefinition = "TEXT")
    private String serverSignature;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public WalletAuthorizationEntity() {}

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }

    public String getAccountVpa() { return accountVpa; }
    public void setAccountVpa(String accountVpa) { this.accountVpa = accountVpa; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public BigDecimal getAuthorizedBalance() { return authorizedBalance; }
    public void setAuthorizedBalance(BigDecimal authorizedBalance) { this.authorizedBalance = authorizedBalance; }

    public BigDecimal getMaxPerTxAmount() { return maxPerTxAmount; }
    public void setMaxPerTxAmount(BigDecimal maxPerTxAmount) { this.maxPerTxAmount = maxPerTxAmount; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getServerNonce() { return serverNonce; }
    public void setServerNonce(String serverNonce) { this.serverNonce = serverNonce; }

    public String getServerKeyId() { return serverKeyId; }
    public void setServerKeyId(String serverKeyId) { this.serverKeyId = serverKeyId; }

    public String getServerSignature() { return serverSignature; }
    public void setServerSignature(String serverSignature) { this.serverSignature = serverSignature; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
