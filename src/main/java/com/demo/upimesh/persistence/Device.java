package com.demo.upimesh.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity mapping client hardware device metadata.
 */
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "account_vpa", nullable = false)
    private String accountVpa;

    @Column(name = "public_key_base64", nullable = false, columnDefinition = "TEXT")
    private String publicKeyBase64;

    @Column(name = "device_type", nullable = false)
    private String deviceType = "SMARTPHONE";

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Device() {}

    public Device(String deviceId, String accountVpa, String publicKeyBase64) {
        this.deviceId = deviceId;
        this.accountVpa = accountVpa;
        this.publicKeyBase64 = publicKeyBase64;
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAccountVpa() { return accountVpa; }
    public void setAccountVpa(String accountVpa) { this.accountVpa = accountVpa; }

    public String getPublicKeyBase64() { return publicKeyBase64; }
    public void setPublicKeyBase64(String publicKeyBase64) { this.publicKeyBase64 = publicKeyBase64; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
