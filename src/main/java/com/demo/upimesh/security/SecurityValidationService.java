package com.demo.upimesh.security;

import com.demo.upimesh.crypto.Ed25519CryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.ProtocolVersion;
import com.demo.upimesh.protocol.WalletAuthorization;
import com.demo.upimesh.protocol.exception.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.time.Instant;

/**
 * Validates cryptographic signatures, wallet authorizations, timestamp freshness, and spending allowances.
 */
@Service
public class SecurityValidationService {

    @Autowired private Ed25519CryptoService ed25519Service;
    @Autowired private ServerKeyHolder serverKey;

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    public void validateInstruction(PaymentInstruction instruction) {
        if (instruction == null) {
            throw new IllegalArgumentException("PaymentInstruction cannot be null");
        }

        // 1. Protocol Version Check
        ProtocolVersion version = new ProtocolVersion(
                instruction.getVersion() == null ? "1.0.0" : instruction.getVersion());
        if (!ProtocolVersion.V1_0.isCompatible(version)) {
            throw new InvalidProtocolVersionException("Incompatible protocol version: " + version);
        }

        // 2. Replay Protection / Timestamp Freshness Check
        if (instruction.getSignedAt() == null) {
            throw new StalePacketException("Missing signedAt timestamp");
        }

        long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;
        if (ageSeconds > maxAgeSeconds) {
            throw new StalePacketException("Packet expired (age: " + ageSeconds + "s)");
        }
        if (ageSeconds < -300) {
            throw new StalePacketException("Future-dated transaction timestamp");
        }

        // 3. Wallet Authorization Presence & Server Signature Verification
        WalletAuthorization auth = instruction.getWalletAuthorization();
        if (auth == null) {
            throw new CryptographicValidationException("Missing wallet authorization token");
        }

        boolean validServerSig = ed25519Service.verify(
                auth.getCanonicalData(),
                auth.getServerSignature(),
                serverKey.getEd25519PublicKey()
        );
        if (!validServerSig) {
            throw new CryptographicValidationException("Invalid server authorization signature");
        }

        // 4. Wallet Authorization Expiry Check
        if (auth.getExpiry() == null || auth.getExpiry() < instruction.getSignedAt()) {
            throw new StalePacketException("Wallet authorization expired before payment was signed");
        }

        // 5. Allowance Bound Check
        if (instruction.getAmount() == null || instruction.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Invalid payment amount");
        }
        if (auth.getAuthorizedBalance() != null && instruction.getAmount().compareTo(auth.getAuthorizedBalance()) > 0) {
            throw new InsufficientOfflineBalanceException("Payment amount ₹" + instruction.getAmount() +
                    " exceeds authorized offline allowance of ₹" + auth.getAuthorizedBalance());
        }

        // 6. Device Transaction Signature Verification
        if (instruction.getDeviceSignature() == null || instruction.getDeviceSignature().isBlank()) {
            throw new CryptographicValidationException("Missing device transaction signature");
        }

        PublicKey devicePublicKey = ed25519Service.decodePublicKey(auth.getDevicePublicKeyBase64());
        boolean validDeviceSig = ed25519Service.verify(
                instruction.getCanonicalData(),
                instruction.getDeviceSignature(),
                devicePublicKey
        );
        if (!validDeviceSig) {
            throw new CryptographicValidationException("Invalid device transaction signature");
        }
    }
}
