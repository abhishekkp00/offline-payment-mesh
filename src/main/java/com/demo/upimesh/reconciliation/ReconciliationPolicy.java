package com.demo.upimesh.reconciliation;

/**
 * Strategy policy controlling how conflicting offline transactions are reconciled.
 */
public enum ReconciliationPolicy {

    /**
     * Ingestion arrival order determines acceptance (Default).
     * The first valid transaction to reach the backend claims available pre-funded allowance.
     * Subsequent conflicting transactions are marked CONFLICTED.
     */
    FIRST_ARRIVED_WINS,

    /**
     * Client signedAt timestamp determines priority.
     * The transaction with the earlier client signature timestamp claims available allowance.
     */
    TIMESTAMP_PRIORITY
}
