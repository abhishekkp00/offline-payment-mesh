package com.demo.upimesh.wallet;

import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.TransactionId;
import com.demo.upimesh.protocol.WalletId;
import com.demo.upimesh.protocol.exception.UnknownWalletException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing pre-funded offline wallets and payment instruction generation.
 */
@Service
public class WalletService {

    private final Map<String, Wallet> walletsByVpa = new ConcurrentHashMap<>();

    public Wallet registerWallet(String vpa, BigDecimal initialPreFundedBalance) {
        WalletId walletId = WalletId.of("wlet-" + vpa.replaceAll("[^a-zA-Z0-9]", "-"));
        Wallet wallet = new Wallet(walletId, vpa, initialPreFundedBalance);
        walletsByVpa.put(vpa, wallet);
        return wallet;
    }

    public Wallet getWalletByVpa(String vpa) {
        Wallet wallet = walletsByVpa.get(vpa);
        if (wallet == null) {
            // Auto-provision default wallet if unknown
            return registerWallet(vpa, new BigDecimal("1000.00"));
        }
        return wallet;
    }

    public PaymentInstruction prepareOfflineInstruction(String senderVpa, String receiverVpa,
                                                         BigDecimal amount, String pin) {
        Wallet wallet = getWalletByVpa(senderVpa);
        wallet.reserveOfflineFunds(amount);

        String pinHash = String.valueOf((pin == null ? "" : pin).hashCode());
        String nonce = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        return new PaymentInstruction(
                "1.0.0",
                TransactionId.generate().value(),
                senderVpa,
                receiverVpa,
                amount,
                pinHash,
                nonce,
                now
        );
    }
}
