package com.demo.upimesh.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Cryptographically signed server token authorizing offline wallet spending.
 */
public class WalletAuthorization {

    private String version = "1.0.0";
    private String walletId;
    private String accountVpa;
    private String deviceId;
    private String devicePublicKeyBase64;
    private BigDecimal authorizedBalance;
    private Long issuedAt;
    private Long expiry;
    private String serverNonce;
    private String serverKeyId = "server-ed25519-v1";
    private String serverSignature;

    public WalletAuthorization() {}

    public WalletAuthorization(String walletId, String accountVpa, String deviceId,
                               String devicePublicKeyBase64, BigDecimal authorizedBalance,
                               Long issuedAt, Long expiry, String serverNonce) {
        this.version = "1.0.0";
        this.walletId = walletId;
        this.accountVpa = accountVpa;
        this.deviceId = deviceId;
        this.devicePublicKeyBase64 = devicePublicKeyBase64;
        this.authorizedBalance = authorizedBalance;
        this.issuedAt = issuedAt;
        this.expiry = expiry;
        this.serverNonce = serverNonce;
        this.serverKeyId = "server-ed25519-v1";
    }

    @JsonIgnore
    public byte[] getCanonicalData() {
        String raw = String.join("|",
                version == null ? "" : version,
                walletId == null ? "" : walletId,
                accountVpa == null ? "" : accountVpa,
                deviceId == null ? "" : deviceId,
                devicePublicKeyBase64 == null ? "" : devicePublicKeyBase64,
                authorizedBalance == null ? "" : authorizedBalance.toPlainString(),
                issuedAt == null ? "" : String.valueOf(issuedAt),
                expiry == null ? "" : String.valueOf(expiry),
                serverNonce == null ? "" : serverNonce,
                serverKeyId == null ? "" : serverKeyId
        );
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }

    public String getAccountVpa() { return accountVpa; }
    public void setAccountVpa(String accountVpa) { this.accountVpa = accountVpa; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDevicePublicKeyBase64() { return devicePublicKeyBase64; }
    public void setDevicePublicKeyBase64(String devicePublicKeyBase64) { this.devicePublicKeyBase64 = devicePublicKeyBase64; }

    public BigDecimal getAuthorizedBalance() { return authorizedBalance; }
    public void setAuthorizedBalance(BigDecimal authorizedBalance) { this.authorizedBalance = authorizedBalance; }

    public Long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Long issuedAt) { this.issuedAt = issuedAt; }

    public Long getExpiry() { return expiry; }
    public void setExpiry(Long expiry) { this.expiry = expiry; }

    public String getServerNonce() { return serverNonce; }
    public void setServerNonce(String serverNonce) { this.serverNonce = serverNonce; }

    public String getServerKeyId() { return serverKeyId; }
    public void setServerKeyId(String serverKeyId) { this.serverKeyId = serverKeyId; }

    public String getServerSignature() { return serverSignature; }
    public void setServerSignature(String serverSignature) { this.serverSignature = serverSignature; }
}
