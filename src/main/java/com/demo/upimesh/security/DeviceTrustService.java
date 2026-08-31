package com.demo.upimesh.security;

import com.demo.upimesh.persistence.Device;
import com.demo.upimesh.protocol.exception.CryptographicValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing hardware device identity trust, key rotation, and revocation.
 */
@Service
public class DeviceTrustService {

    private static final Logger log = LoggerFactory.getLogger(DeviceTrustService.class);

    private final Map<String, Device> deviceRegistry = new ConcurrentHashMap<>();

    public DeviceTrustService() {
        registerDevice("dev-alice-001", "alice@demo", "MCowBQYDK2VwAyEA...");
        registerDevice("dev-bob-001",   "bob@demo",   "MCowBQYDK2VwAyEA...");
        registerDevice("dev-carol-001", "carol@demo", "MCowBQYDK2VwAyEA...");
        registerDevice("dev-dave-001",  "dave@demo",  "MCowBQYDK2VwAyEA...");
    }

    public void registerDevice(String deviceId, String accountVpa, String publicKeyBase64) {
        Device device = new Device(deviceId, accountVpa, publicKeyBase64);
        device.setStatus("ACTIVE");
        deviceRegistry.put(deviceId, device);
        log.info("Registered hardware device {} for account {}", deviceId, accountVpa);
    }

    public void rotateDeviceKey(String deviceId, String newPublicKeyBase64) {
        Device device = deviceRegistry.get(deviceId);
        if (device == null) {
            device = new Device(deviceId, "unknown", newPublicKeyBase64);
            deviceRegistry.put(deviceId, device);
        }
        device.setPublicKeyBase64(newPublicKeyBase64);
        log.info("Rotated device key for {}", deviceId);
    }

    public void revokeDevice(String deviceId) {
        Device device = deviceRegistry.computeIfAbsent(deviceId, id -> new Device(id, "unknown", "REVOKED_PUB_KEY"));
        device.setStatus("REVOKED");
        log.warn("Revoked hardware device {}", deviceId);
    }

    public Device validateDeviceTrust(String deviceId) {
        Device device = deviceRegistry.get(deviceId);

        if (device == null) {
            // Auto-register active valid wallet devices on first sight unless explicitly revoked
            if (deviceId != null && (deviceId.startsWith("dev-") || deviceId.startsWith("phone-"))) {
                registerDevice(deviceId, "auto-registered", "MCowBQYDK2VwAyEA...");
                device = deviceRegistry.get(deviceId);
            } else {
                throw new CryptographicValidationException("Unknown or unregistered hardware device: " + deviceId);
            }
        }

        if ("REVOKED".equalsIgnoreCase(device.getStatus())) {
            throw new CryptographicValidationException("Hardware device " + deviceId + " has been REVOKED");
        }

        if ("EXPIRED".equalsIgnoreCase(device.getStatus())) {
            throw new CryptographicValidationException("Hardware device " + deviceId + " status is EXPIRED");
        }

        return device;
    }

    public Optional<Device> getDevice(String deviceId) {
        return Optional.ofNullable(deviceRegistry.get(deviceId));
    }
}
