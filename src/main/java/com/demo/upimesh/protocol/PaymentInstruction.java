package com.demo.upimesh.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Payment payload containing offline payment instruction, signed wallet authorization token,
 * and device asymmetric signature.
 */
public class PaymentInstruction {

    private String version = "1.0.0";
    private String transactionId;
    private WalletAuthorization walletAuthorization;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String pinHash;
    private String nonce;            // Client UUID nonce
    private Long signedAt;           // Epoch millis
    private String deviceSignature;  // Base64 Ed25519 device signature

    public PaymentInstruction() {}

    public PaymentInstruction(String senderVpa, String receiverVpa, BigDecimal amount,
                              String pinHash, String nonce, Long signedAt) {
        this("1.0.0", TransactionId.generate().value(), senderVpa, receiverVpa, amount, pinHash, nonce, signedAt);
    }

    public PaymentInstruction(String version, String transactionId, String senderVpa, String receiverVpa,
                              BigDecimal amount, String pinHash, String nonce, Long signedAt) {
        this.version = version == null ? "1.0.0" : version;
        this.transactionId = transactionId == null ? TransactionId.generate().value() : transactionId;
        this.senderVpa = senderVpa;
        this.receiverVpa = receiverVpa;
        this.amount = amount;
        this.pinHash = pinHash;
        this.nonce = nonce;
        this.signedAt = signedAt;
    }

    @JsonIgnore
    public byte[] getCanonicalData() {
        String walletIdStr = walletAuthorization == null ? "" :
                (walletAuthorization.getWalletId() == null ? "" : walletAuthorization.getWalletId());
        String raw = String.join("|",
                version == null ? "" : version,
                transactionId == null ? "" : transactionId,
                walletIdStr,
                senderVpa == null ? "" : senderVpa,
                receiverVpa == null ? "" : receiverVpa,
                amount == null ? "" : amount.toPlainString(),
                nonce == null ? "" : nonce,
                signedAt == null ? "" : String.valueOf(signedAt)
        );
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public WalletAuthorization getWalletAuthorization() { return walletAuthorization; }
    public void setWalletAuthorization(WalletAuthorization walletAuthorization) { this.walletAuthorization = walletAuthorization; }

    public String getSenderVpa() { return senderVpa; }
    public void setSenderVpa(String senderVpa) { this.senderVpa = senderVpa; }

    public String getReceiverVpa() { return receiverVpa; }
    public void setReceiverVpa(String receiverVpa) { this.receiverVpa = receiverVpa; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public Long getSignedAt() { return signedAt; }
    public void setSignedAt(Long signedAt) { this.signedAt = signedAt; }

    public String getDeviceSignature() { return deviceSignature; }
    public void setDeviceSignature(String deviceSignature) { this.deviceSignature = deviceSignature; }
}
