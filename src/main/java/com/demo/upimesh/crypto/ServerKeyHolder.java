package com.demo.upimesh.crypto;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Server key storage for asymmetric encryption (RSA-2048) and Ed25519 wallet authorization signing.
 */
@Component
public class ServerKeyHolder {

    private static final Logger log = LoggerFactory.getLogger(ServerKeyHolder.class);

    private KeyPair rsaKeyPair;
    private KeyPair ed25519KeyPair;

    @Autowired
    private Ed25519CryptoService ed25519Service;

    @PostConstruct
    public void init() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.rsaKeyPair = gen.generateKeyPair();

        this.ed25519KeyPair = ed25519Service.generateKeyPair();

        log.info("Server keys initialized: RSA-2048 (Encryption) + Ed25519 (Wallet Authorization Signing)");
    }

    public PublicKey getPublicKey() {
        return rsaKeyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return rsaKeyPair.getPrivate();
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());
    }

    public PublicKey getEd25519PublicKey() {
        return ed25519KeyPair.getPublic();
    }

    public PrivateKey getEd25519PrivateKey() {
        return ed25519KeyPair.getPrivate();
    }

    public String getEd25519PublicKeyBase64() {
        return ed25519Service.encodePublicKey(ed25519KeyPair.getPublic());
    }
}
