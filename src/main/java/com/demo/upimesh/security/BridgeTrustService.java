package com.demo.upimesh.security;

import com.demo.upimesh.persistence.BridgeNodeEntity;
import com.demo.upimesh.protocol.exception.CryptographicValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing DTN Gateway Bridge node identity, authentication, and revocation.
 */
@Service
public class BridgeTrustService {

    private static final Logger log = LoggerFactory.getLogger(BridgeTrustService.class);

    private final Map<String, BridgeNodeEntity> bridgeRegistry = new ConcurrentHashMap<>();

    public BridgeTrustService() {
        registerBridge("phone-bridge", "Gateway Bridge Node Alpha");
        registerBridge("bridge-1", "Gateway Bridge Node 1");
        registerBridge("bridge-2", "Gateway Bridge Node 2");
        registerBridge("bridge-3", "Gateway Bridge Node 3");
    }

    public void registerBridge(String bridgeId, String nodeName) {
        BridgeNodeEntity bridge = new BridgeNodeEntity(bridgeId, nodeName);
        bridge.setStatus("ACTIVE");
        bridgeRegistry.put(bridgeId, bridge);
        log.info("Registered DTN Gateway Bridge Node {} ({})", bridgeId, nodeName);
    }

    public void revokeBridge(String bridgeId) {
        BridgeNodeEntity bridge = bridgeRegistry.computeIfAbsent(bridgeId, id -> new BridgeNodeEntity(id, "Revoked Bridge Node"));
        bridge.setStatus("REVOKED");
        bridge.setOnline(false);
        log.warn("Revoked DTN Gateway Bridge Node {}", bridgeId);
    }

    public BridgeNodeEntity validateBridgeTrust(String bridgeId) {
        BridgeNodeEntity bridge = bridgeRegistry.get(bridgeId);

        if (bridge == null) {
            if (bridgeId != null && (bridgeId.startsWith("bridge-") || bridgeId.startsWith("phone-") || bridgeId.startsWith("gateway-"))) {
                registerBridge(bridgeId, "Gateway Bridge Node");
                bridge = bridgeRegistry.get(bridgeId);
            } else {
                throw new CryptographicValidationException("Unauthorized or unregistered DTN Bridge Node: " + bridgeId);
            }
        }

        if ("REVOKED".equalsIgnoreCase(bridge.getStatus())) {
            throw new CryptographicValidationException("DTN Bridge Node " + bridgeId + " has been REVOKED");
        }

        bridge.setLastHeartbeat(Instant.now());
        bridge.setTotalUploads(bridge.getTotalUploads() + 1);
        return bridge;
    }

    public Optional<BridgeNodeEntity> getBridge(String bridgeId) {
        return Optional.ofNullable(bridgeRegistry.get(bridgeId));
    }
}
