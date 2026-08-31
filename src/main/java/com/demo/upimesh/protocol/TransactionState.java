package com.demo.upimesh.protocol;

/**
 * Processing lifecycle states of an offline transaction payload.
 *
 * Successful path: RECEIVED -> PROCESSING -> VALIDATED -> SETTLED
 * Terminal/Error paths: REJECTED, EXPIRED, DUPLICATE, CONFLICTED, OVERSPENT, FAILED_RETRYABLE, FAILED_PERMANENT
 */
public enum TransactionState {
    RECEIVED,
    PROCESSING,
    VALIDATED,
    SETTLED,

    // Terminal / Double-Spend & Error States
    REJECTED,
    EXPIRED,
    DUPLICATE,
    CONFLICTED,
    OVERSPENT,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
}
