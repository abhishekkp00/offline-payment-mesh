package com.demo.upimesh.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity mapping DTN bridge gateway registry.
 */
@Entity
@Table(name = "bridge_nodes")
public class BridgeNodeEntity {

    @Id
    @Column(name = "node_id")
    private String nodeId;

    @Column(name = "node_name", nullable = false)
    private String nodeName;

    @Column(name = "is_online", nullable = false)
    private boolean isOnline = true;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat = Instant.now();

    @Column(name = "total_uploads", nullable = false)
    private int totalUploads = 0;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public BridgeNodeEntity() {}

    public BridgeNodeEntity(String nodeId, String nodeName) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (lastHeartbeat == null) lastHeartbeat = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public int getTotalUploads() { return totalUploads; }
    public void setTotalUploads(int totalUploads) { this.totalUploads = totalUploads; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
