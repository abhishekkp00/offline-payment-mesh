package com.demo.upimesh.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity mapping transaction state lifecycle audit event streams.
 */
@Entity
@Table(name = "transaction_events")
public class TransactionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "packet_hash", nullable = false)
    private String packetHash;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp = Instant.now();

    public TransactionEventEntity() {}

    public TransactionEventEntity(Long transactionId, String packetHash, String eventType, String details) {
        this.transactionId = transactionId;
        this.packetHash = packetHash;
        this.eventType = eventType;
        this.details = details;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getPacketHash() { return packetHash; }
    public void setPacketHash(String packetHash) { this.packetHash = packetHash; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Instant eventTimestamp) { this.eventTimestamp = eventTimestamp; }
}
