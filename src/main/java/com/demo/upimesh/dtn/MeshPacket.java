package com.demo.upimesh.dtn;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTN Bundle (Over-the-wire packet).
 *
 * Intermediate DTN relay nodes can inspect outer fields (version, packetId, ttl, keyId, originDeviceId, createdAt)
 * for store-carry-forward routing and deduplication, but cannot read or tamper with ciphertext.
 */
public class MeshPacket {

    private String version = "1.0.0";

    @NotBlank
    private String packetId; // PacketId UUID

    private String keyId = "key-server-rsa-2048";

    private String originDeviceId = "phone-unknown";

    @Min(0)
    private int ttl; // Hops remaining; relays decrement this counter

    @NotNull
    private Long createdAt; // epoch millis

    @NotBlank
    private String ciphertext; // base64(RSA-encrypted AES key + AES-GCM ciphertext)

    public MeshPacket() {}

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getPacketId() { return packetId; }
    public void setPacketId(String packetId) { this.packetId = packetId; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getOriginDeviceId() { return originDeviceId; }
    public void setOriginDeviceId(String originDeviceId) { this.originDeviceId = originDeviceId; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String ciphertext) { this.ciphertext = ciphertext; }
}
