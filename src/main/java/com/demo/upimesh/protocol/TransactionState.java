package com.demo.upimesh.protocol;

/**
 * Lifecycle states of an offline transaction payload.
 */
public enum TransactionState {
    CREATED,
    SIGNED,
    PENDING_RELAY,
    INGESTED,
    VERIFIED,
    SETTLED,
    REJECTED,
    DUPLICATE_DROPPED,
    EXPIRED
}
