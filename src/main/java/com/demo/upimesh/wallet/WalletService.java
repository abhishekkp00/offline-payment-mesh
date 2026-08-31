package com.demo.upimesh.wallet;

import com.demo.upimesh.crypto.Ed25519CryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.TransactionId;
import com.demo.upimesh.protocol.WalletAuthorization;
import com.demo.upimesh.protocol.WalletId;
import com.demo.upimesh.protocol.exception.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing online wallet authorization issuance and offline payment creation.
 */
@Service
public class WalletService {

    private final Map<String, Wallet> walletsByVpa = new ConcurrentHashMap<>();

    @Autowired private Ed25519CryptoService ed25519Service;
    @Autowired private ServerKeyHolder serverKey;

    public Wallet registerWallet(String vpa, BigDecimal initialPreFundedBalance) {
        WalletId walletId = WalletId.of("wlet-" + vpa.replaceAll("[^a-zA-Z0-9]", "-"));
        Wallet wallet = new Wallet(walletId, vpa, initialPreFundedBalance, ed25519Service.generateKeyPair());
        walletsByVpa.put(vpa, wallet);
        return wallet;
    }

    public Wallet getWalletByVpa(String vpa) {
        Wallet wallet = walletsByVpa.get(vpa);
        if (wallet == null) {
            return registerWallet(vpa, new BigDecimal("1000.00"));
        }
        return wallet;
    }

    /**
     * ONLINE: Server authorizes an offline wallet spending allowance and issues a signed WalletAuthorization token.
     */
    public WalletAuthorization issueWalletAuthorization(String accountVpa, BigDecimal allowanceAmount, long validitySeconds) {
        Wallet wallet = getWalletByVpa(accountVpa);
        String devicePublicKeyBase64 = ed25519Service.encodePublicKey(wallet.getDeviceKeyPair().getPublic());

        long now = Instant.now().toEpochMilli();
        long expiry = now + (validitySeconds * 1000);
        String serverNonce = UUID.randomUUID().toString();

        WalletAuthorization auth = new WalletAuthorization(
                wallet.getWalletId().value(),
                accountVpa,
                "dev-" + wallet.getWalletId().value(),
                devicePublicKeyBase64,
                allowanceAmount,
                now,
                expiry,
                serverNonce
        );

        byte[] canonicalData = auth.getCanonicalData();
        String serverSig = ed25519Service.sign(canonicalData, serverKey.getEd25519PrivateKey());
        auth.setServerSignature(serverSig);

        wallet.setActiveAuthorization(auth);
        return auth;
    }

    /**
     * OFFLINE: Client device verifies local authorization token, reserves funds, and signs payment instruction using device Ed25519 key.
     */
    public PaymentInstruction prepareOfflineInstruction(String senderVpa, String receiverVpa,
                                                         BigDecimal amount, String pin) {
        Wallet wallet = getWalletByVpa(senderVpa);

        WalletAuthorization auth = wallet.getActiveAuthorization();
        if (auth == null) {
            // Auto-issue standard 24h authorization for simulation convenience
            auth = issueWalletAuthorization(senderVpa, new BigDecimal("1000.00"), 86400);
        }

        // 1. Verify Local Server Authorization Signature
        boolean validAuthSig = ed25519Service.verify(
                auth.getCanonicalData(),
                auth.getServerSignature(),
                serverKey.getEd25519PublicKey()
        );
        if (!validAuthSig) {
            throw new CryptographicValidationException("Invalid server authorization signature on offline wallet token");
        }

        // 2. Check Expiry
        if (auth.getExpiry() <= Instant.now().toEpochMilli()) {
            throw new StalePacketException("Wallet authorization expired at " + auth.getExpiry());
        }

        // 3. Reserve Balance locally
        wallet.reserveOfflineFunds(amount);

        String pinHash = String.valueOf((pin == null ? "" : pin).hashCode());
        String clientNonce = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        PaymentInstruction instruction = new PaymentInstruction(
                "1.0.0",
                TransactionId.generate().value(),
                senderVpa,
                receiverVpa,
                amount,
                pinHash,
                clientNonce,
                now
        );
        instruction.setWalletAuthorization(auth);

        // 4. Sign Payment Instruction with Device Ed25519 Private Key
        byte[] canonicalTx = instruction.getCanonicalData();
        String deviceSig = ed25519Service.sign(canonicalTx, wallet.getDeviceKeyPair().getPrivate());
        instruction.setDeviceSignature(deviceSig);

        return instruction;
    }
}
