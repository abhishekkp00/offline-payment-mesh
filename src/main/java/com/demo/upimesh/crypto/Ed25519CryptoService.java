package com.demo.upimesh.crypto;

import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Modern Cryptographic Service providing Ed25519 asymmetric signatures
 * and utility functions using native Java 21 security providers.
 */
@Service
public class Ed25519CryptoService {

    public static final String ALGORITHM = "Ed25519";

    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 key generation failed", e);
        }
    }

    public String sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initSign(privateKey);
            sig.update(data);
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Ed25519 signature generation failed", e);
        }
    }

    public boolean verify(byte[] data, String base64Signature, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public PublicKey decodePublicKey(String base64PublicKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Base64 Ed25519 public key", e);
        }
    }

    public String encodePrivateKey(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    public PrivateKey decodePrivateKey(String base64PrivateKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Base64 Ed25519 private key", e);
        }
    }
}
