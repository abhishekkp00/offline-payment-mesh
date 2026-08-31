package com.demo.upimesh.wallet;

import com.demo.upimesh.protocol.WalletId;
import com.demo.upimesh.protocol.WalletState;
import com.demo.upimesh.protocol.exception.InsufficientOfflineBalanceException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Pre-funded offline digital wallet representation.
 */
public class Wallet {

    private final WalletId walletId;
    private final String holderVpa;
    private BigDecimal preFundedBalance;
    private BigDecimal reservedOfflineBalance;
    private WalletState state;
    private Instant lastSyncedAt;

    public Wallet(WalletId walletId, String holderVpa, BigDecimal initialBalance) {
        this.walletId = Objects.requireNonNull(walletId, "walletId required");
        this.holderVpa = Objects.requireNonNull(holderVpa, "holderVpa required");
        this.preFundedBalance = Objects.requireNonNull(initialBalance, "initialBalance required");
        this.reservedOfflineBalance = BigDecimal.ZERO;
        this.state = WalletState.ACTIVE;
        this.lastSyncedAt = Instant.now();
    }

    public synchronized BigDecimal getAvailableOfflineBalance() {
        return preFundedBalance.subtract(reservedOfflineBalance);
    }

    public synchronized void reserveOfflineFunds(BigDecimal amount) {
        if (state != WalletState.ACTIVE) {
            throw new IllegalStateException("Wallet is not active: " + state);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Reservation amount must be positive");
        }
        if (getAvailableOfflineBalance().compareTo(amount) < 0) {
            throw new InsufficientOfflineBalanceException(
                    "Insufficient offline balance in wallet " + walletId + ". Available: ₹" +
                    getAvailableOfflineBalance() + ", Requested: ₹" + amount);
        }
        this.reservedOfflineBalance = this.reservedOfflineBalance.add(amount);
    }

    public synchronized void commitReservation(BigDecimal amount) {
        if (reservedOfflineBalance.compareTo(amount) >= 0) {
            reservedOfflineBalance = reservedOfflineBalance.subtract(amount);
            preFundedBalance = preFundedBalance.subtract(amount);
        }
    }

    public synchronized void creditPreFundedBalance(BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            preFundedBalance = preFundedBalance.add(amount);
        }
    }

    public WalletId getWalletId() { return walletId; }
    public String getHolderVpa() { return holderVpa; }
    public synchronized BigDecimal getPreFundedBalance() { return preFundedBalance; }
    public synchronized BigDecimal getReservedOfflineBalance() { return reservedOfflineBalance; }
    public synchronized WalletState getState() { return state; }
    public synchronized void setState(WalletState state) { this.state = state; }
    public synchronized Instant getLastSyncedAt() { return lastSyncedAt; }
    public synchronized void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
