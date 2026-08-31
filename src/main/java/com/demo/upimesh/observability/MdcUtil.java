package com.demo.upimesh.observability;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility for populating Mapped Diagnostic Context (MDC) fields in structured logs.
 */
public class MdcUtil {

    public static final String TX_ID = "transactionId";
    public static final String PKT_ID = "packetId";
    public static final String WALLET_ID = "walletId";
    public static final String BRIDGE_ID = "bridgeId";
    public static final String DEVICE_ID = "deviceId";
    public static final String STATE = "processingState";
    public static final String CORRELATION_ID = "correlationId";

    public static void setContext(String txId, String pktId, String walletId, String bridgeId, String deviceId, String state) {
        if (txId != null) MDC.put(TX_ID, txId);
        if (pktId != null) MDC.put(PKT_ID, pktId);
        if (walletId != null) MDC.put(WALLET_ID, walletId);
        if (bridgeId != null) MDC.put(BRIDGE_ID, bridgeId);
        if (deviceId != null) MDC.put(DEVICE_ID, deviceId);
        if (state != null) MDC.put(STATE, state);
        if (MDC.get(CORRELATION_ID) == null) {
            MDC.put(CORRELATION_ID, "corr-" + UUID.randomUUID().toString().substring(0, 8));
        }
    }

    public static void clear() {
        MDC.clear();
    }
}
