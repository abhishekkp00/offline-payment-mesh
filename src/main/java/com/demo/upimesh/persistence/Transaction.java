package com.demo.upimesh.persistence;

import com.demo.upimesh.protocol.TransactionState;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Durable entity and audit record of every transaction through its processing lifecycle.
 */
@Entity
@Table(name = "transactions",
        indexes = { @Index(name = "idx_packet_hash", columnList = "packetHash", unique = true) })
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String packetHash; // SHA-256 hex of encrypted packet

    @Column(nullable = true)
    private String senderVpa;

    @Column(nullable = true)
    private String receiverVpa;

    @Column(nullable = true, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = true)
    private Instant signedAt; // When sender originally signed it (offline)

    @Column(nullable = true)
    private Instant settledAt; // When backend settled it

    @Column(nullable = false)
    private String bridgeNodeId; // Delivered bridge node ID

    @Column(nullable = false)
    private int hopCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionState state = TransactionState.RECEIVED;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = true)
    private String processingNode;

    @Column(nullable = true, length = 512)
    private String failureReason;

    public Transaction() {}

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPacketHash() { return packetHash; }
    public void setPacketHash(String packetHash) { this.packetHash = packetHash; }

    public String getSenderVpa() { return senderVpa; }
    public void setSenderVpa(String senderVpa) { this.senderVpa = senderVpa; }

    public String getReceiverVpa() { return receiverVpa; }
    public void setReceiverVpa(String receiverVpa) { this.receiverVpa = receiverVpa; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }

    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }

    public String getBridgeNodeId() { return bridgeNodeId; }
    public void setBridgeNodeId(String bridgeNodeId) { this.bridgeNodeId = bridgeNodeId; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public TransactionState getState() { return state; }
    public void setState(TransactionState state) { this.state = state; }

    // Convenience getter/setter for backwards compatibility
    public TransactionState getStatus() { return state; }
    public void setStatus(TransactionState state) { this.state = state; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getProcessingNode() { return processingNode; }
    public void setProcessingNode(String processingNode) { this.processingNode = processingNode; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
