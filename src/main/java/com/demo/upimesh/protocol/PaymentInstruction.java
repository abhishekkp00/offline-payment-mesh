package com.demo.upimesh.protocol;

import java.math.BigDecimal;

/**
 * Encrypted payload containing signed payment instruction details.
 */
public class PaymentInstruction {

    private String version = "1.0.0";
    private String transactionId;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String pinHash;
    private String nonce;     // UUID, unique per payment intent
    private Long signedAt;    // epoch millis, when sender signed

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

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

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
}
