package com.demo.upimesh.protocol;

/**
 * Classification taxonomy distinguishing transaction outcomes at server intake & reconciliation time.
 */
public enum TransactionClassification {
    LEGITIMATE_SETTLED("Valid transaction within pre-funded allowance, settled successfully"),
    DUPLICATE_TRANSACTION("Duplicate bundle payload delivered via redundant DTN relay paths"),
    REPLAY_ATTACK("Re-transmitted nonce or timestamp exceeding maximum freshness window"),
    FORGED_TRANSACTION("Invalid server authorization signature or device signature mismatch"),
    OVERSPENDING("Single transaction amount exceeds remaining authorized wallet allowance"),
    CONFLICTING_OFFLINE_TRANSACTIONS("Multiple distinct transactions signed offline against same allowance exceeding total limit");

    private final String description;

    TransactionClassification(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
