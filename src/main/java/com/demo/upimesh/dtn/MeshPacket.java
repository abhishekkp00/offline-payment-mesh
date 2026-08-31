package com.demo.upimesh.dtn;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTN Bundle format containing unencrypted routing metadata and AES-256-GCM encrypted payload ciphertext.
 *
 * Unencrypted metadata (protocolVersion, packetId, transactionId, walletId, createdAt) is cryptographically
 * bound to the ciphertext via AES-GCM Additional Authenticated Data (AAD).
 */
public class MeshPacket {

    private String version = "1.0.0";

    @NotBlank
    private String packetId; // Packet UUID

    private String transactionId; // Transaction UUID

    private String walletId; // Wallet ID string

    private String keyId = "key-server-rsa-2048";

    private String originDeviceId = "phone-unknown";

    @Min(0)
    private int ttl = 5; // Hops remaining

    private int hopCount = 0; // Hops traveled

    @NotNull
    private Long createdAt; // epoch millis

    @NotBlank
    private String ciphertext; // base64(RSA key + GCM IV + AES ciphertext + GCM Tag)

    public MeshPacket() {}

    @JsonIgnore
    public byte[] getCanonicalAad() {
        return HybridCryptoService.computeCanonicalAad(
                version,
                packetId,
                transactionId,
                walletId,
                createdAt == null ? 0L : createdAt
        );
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getPacketId() { return packetId; }
    public void setPacketId(String packetId) { this.packetId = packetId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getOriginDeviceId() { return originDeviceId; }
    public void setOriginDeviceId(String originDeviceId) { this.originDeviceId = originDeviceId; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String ciphertext) { this.ciphertext = ciphertext; }
}
