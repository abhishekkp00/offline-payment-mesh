package com.demo.upimesh.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity mapping security audit log records.
 */
@Entity
@Table(name = "audit_records")
public class AuditRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "payload_hash")
    private String payloadHash;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AuditRecordEntity() {}

    public AuditRecordEntity(String eventType, String entityId, String payloadHash, String details) {
        this.eventType = eventType;
        this.entityId = entityId;
        this.payloadHash = payloadHash;
        this.details = details;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
