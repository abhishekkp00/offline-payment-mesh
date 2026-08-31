package com.demo.upimesh.crypto;

import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.protocol.exception.CryptographicValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.Set;

/**
 * Hybrid encryption service utilizing AES-256-GCM with Additional Authenticated Data (AAD)
 * to cryptographically bind unencrypted packet routing metadata to the encrypted payload.
 */
@Service
public class HybridCryptoService {

    public static final String SUPPORTED_KEY_ID = "key-server-rsa-2048";
    public static final int MAX_PACKET_SIZE_BYTES = 65536; // 64 KB size limit to prevent DoS

    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int RSA_ENCRYPTED_KEY_BYTES = 256; // for 2048-bit RSA

    private final SecureRandom rng = new SecureRandom();
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private ServerKeyHolder serverKey;

    public static byte[] computeCanonicalAad(String protocolVersion, String packetId,
                                            String transactionId, String walletId,
                                            long createdAt) {
        String raw = String.join("|",
                protocolVersion == null ? "" : protocolVersion,
                packetId == null ? "" : packetId,
                transactionId == null ? "" : transactionId,
                walletId == null ? "" : walletId,
                String.valueOf(createdAt)
        );
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    public void validateKeyId(String keyId) {
        if (keyId == null || !SUPPORTED_KEY_ID.equals(keyId)) {
            throw new CryptographicValidationException("Unsupported key algorithm or key ID: " + keyId);
        }
    }

    public String encrypt(PaymentInstruction instruction, PublicKey serverPublicKey) throws Exception {
        byte[] aadBytes = computeCanonicalAad(
                instruction.getVersion(),
                instruction.getTransactionId(),
                instruction.getTransactionId(),
                instruction.getWalletAuthorization() == null ? "" : instruction.getWalletAuthorization().getWalletId(),
                instruction.getSignedAt() == null ? 0L : instruction.getSignedAt()
        );
        return encrypt(instruction, serverPublicKey, aadBytes);
    }

    public String encrypt(PaymentInstruction instruction, PublicKey serverPublicKey, byte[] aadBytes) throws Exception {
        byte[] plaintext = json.writeValueAsBytes(instruction);

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_BITS);
        SecretKey aesKey = kg.generateKey();

        byte[] iv = new byte[GCM_IV_BYTES];
        rng.nextBytes(iv);

        Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
        aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        if (aadBytes != null && aadBytes.length > 0) {
            aes.updateAAD(aadBytes);
        }
        byte[] aesCiphertext = aes.doFinal(plaintext);

        Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsa.init(Cipher.ENCRYPT_MODE, serverPublicKey, oaep);
        byte[] encryptedAesKey = rsa.doFinal(aesKey.getEncoded());

        ByteBuffer buf = ByteBuffer.allocate(encryptedAesKey.length + iv.length + aesCiphertext.length);
        buf.put(encryptedAesKey);
        buf.put(iv);
        buf.put(aesCiphertext);

        return Base64.getEncoder().encodeToString(buf.array());
    }

    public PaymentInstruction decrypt(String base64Ciphertext) throws Exception {
        return decrypt(base64Ciphertext, new byte[0]);
    }

    public PaymentInstruction decrypt(String base64Ciphertext, byte[] aadBytes) throws Exception {
        if (base64Ciphertext == null || base64Ciphertext.isBlank()) {
            throw new IllegalArgumentException("Ciphertext cannot be empty");
        }
        if (base64Ciphertext.length() > MAX_PACKET_SIZE_BYTES) {
            throw new CryptographicValidationException("Packet exceeds maximum allowed size limit (" +
                    base64Ciphertext.length() + " > " + MAX_PACKET_SIZE_BYTES + " bytes)");
        }

        byte[] all;
        try {
            all = Base64.getDecoder().decode(base64Ciphertext);
        } catch (IllegalArgumentException e) {
            throw new CryptographicValidationException("Malformed Base64 ciphertext payload", e);
        }

        if (all.length < RSA_ENCRYPTED_KEY_BYTES + GCM_IV_BYTES + GCM_TAG_BITS / 8) {
            throw new IllegalArgumentException("Ciphertext too short");
        }

        byte[] encryptedAesKey = new byte[RSA_ENCRYPTED_KEY_BYTES];
        byte[] iv = new byte[GCM_IV_BYTES];
        byte[] aesCiphertext = new byte[all.length - RSA_ENCRYPTED_KEY_BYTES - GCM_IV_BYTES];

        ByteBuffer buf = ByteBuffer.wrap(all);
        buf.get(encryptedAesKey);
        buf.get(iv);
        buf.get(aesCiphertext);

        Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsa.init(Cipher.DECRYPT_MODE, serverKey.getPrivateKey(), oaep);
        byte[] aesKeyBytes = rsa.doFinal(encryptedAesKey);
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

        Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
        aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        if (aadBytes != null && aadBytes.length > 0) {
            aes.updateAAD(aadBytes);
        }
        byte[] plaintext = aes.doFinal(aesCiphertext);

        return json.readValue(plaintext, PaymentInstruction.class);
    }

    public String hashCiphertext(String base64Ciphertext) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(base64Ciphertext.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
