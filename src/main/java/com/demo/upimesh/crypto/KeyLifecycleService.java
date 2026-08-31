package com.demo.upimesh.crypto;

import com.demo.upimesh.persistence.CryptographicKeyEntity;
import com.demo.upimesh.protocol.exception.CryptographicValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing cryptographic key registration, key rotation, revocation, and expiration.
 */
@Service
public class KeyLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(KeyLifecycleService.class);

    private final Map<String, CryptographicKeyEntity> keyRegistry = new ConcurrentHashMap<>();

    public KeyLifecycleService() {
        // Register default active keys
        registerKey("key-server-rsa-2048", "RSA-2048/OAEP-SHA256", "SERVER_ENCRYPTION", "SERVER_PUBLIC_KEY_PEM", null);
        registerKey("server-ed25519-v1", "Ed25519", "SERVER_SIGNING", "SERVER_ED25519_PUBLIC_KEY_PEM", null);
    }

    public void registerKey(String keyId, String algorithm, String keyType, String publicKeyPem, Instant expiresAt) {
        CryptographicKeyEntity key = new CryptographicKeyEntity(keyId, algorithm, keyType, publicKeyPem);
        key.setExpiresAt(expiresAt);
        key.setStatus("ACTIVE");
        keyRegistry.put(keyId, key);
        log.info("Registered cryptographic key {} (algorithm={}, status=ACTIVE)", keyId, algorithm);
    }

    public void rotateKey(String oldKeyId, String newKeyId, String algorithm, String keyType, String newPublicKeyPem) {
        CryptographicKeyEntity oldKey = keyRegistry.get(oldKeyId);
        if (oldKey != null) {
            oldKey.setStatus("ROTATED"); // Rotated keys remain valid for verification during transition
        }

        registerKey(newKeyId, algorithm, keyType, newPublicKeyPem, null);
        log.info("Rotated key from {} to {}", oldKeyId, newKeyId);
    }

    public void revokeKey(String keyId) {
        CryptographicKeyEntity key = keyRegistry.get(keyId);
        if (key != null) {
            key.setStatus("REVOKED");
            log.warn("Revoked cryptographic key {}", keyId);
        }
    }

    public void validateKeyStatus(String keyId) {
        CryptographicKeyEntity key = keyRegistry.get(keyId);
        if (key == null) {
            throw new CryptographicValidationException("Unknown cryptographic key ID: " + keyId);
        }

        if ("REVOKED".equalsIgnoreCase(key.getStatus())) {
            throw new CryptographicValidationException("Cryptographic key " + keyId + " has been REVOKED");
        }

        if ("EXPIRED".equalsIgnoreCase(key.getStatus()) ||
                (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now()))) {
            key.setStatus("EXPIRED");
            throw new CryptographicValidationException("Cryptographic key " + keyId + " has EXPIRED");
        }
    }

    public Optional<CryptographicKeyEntity> getKey(String keyId) {
        return Optional.ofNullable(keyRegistry.get(keyId));
    }
}
