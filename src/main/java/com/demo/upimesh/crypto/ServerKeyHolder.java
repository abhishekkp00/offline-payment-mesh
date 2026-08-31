package com.demo.upimesh.crypto;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Server key holder with persistent disk/environment storage for asymmetric encryption (RSA-2048)
 * and Ed25519 wallet authorization signing.
 */
@Component
public class ServerKeyHolder {

    private static final Logger log = LoggerFactory.getLogger(ServerKeyHolder.class);

    @Value("${upi.mesh.keys.dir:.keys}")
    private String keysDir;

    private KeyPair rsaKeyPair;
    private KeyPair ed25519KeyPair;

    @Autowired
    private Ed25519CryptoService ed25519Service;

    @PostConstruct
    public void init() throws Exception {
        this.rsaKeyPair = loadOrGenerateRsaKeys();
        this.ed25519KeyPair = loadOrGenerateEd25519Keys();

        log.info("Server keys initialized: RSA-2048 (Encryption) + Ed25519 (Wallet Authorization Signing)");
    }

    private KeyPair loadOrGenerateRsaKeys() throws Exception {
        Path dirPath = Paths.get(keysDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        Path privPath = dirPath.resolve("server_rsa_private.key");
        Path pubPath = dirPath.resolve("server_rsa_public.key");

        if (Files.exists(privPath) && Files.exists(pubPath)) {
            byte[] privBytes = Base64.getDecoder().decode(Files.readString(privPath).trim());
            byte[] pubBytes = Base64.getDecoder().decode(Files.readString(pubPath).trim());

            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            log.info("Loaded persistent server RSA keys from {}", keysDir);
            return new KeyPair(pub, priv);
        }

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        Files.writeString(privPath, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        Files.writeString(pubPath, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        log.info("Generated and saved new server RSA keys to {}", keysDir);

        return pair;
    }

    private KeyPair loadOrGenerateEd25519Keys() throws Exception {
        Path dirPath = Paths.get(keysDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        Path privPath = dirPath.resolve("server_ed25519_private.key");
        Path pubPath = dirPath.resolve("server_ed25519_public.key");

        if (Files.exists(privPath) && Files.exists(pubPath)) {
            byte[] privBytes = Base64.getDecoder().decode(Files.readString(privPath).trim());
            byte[] pubBytes = Base64.getDecoder().decode(Files.readString(pubPath).trim());

            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            log.info("Loaded persistent server Ed25519 keys from {}", keysDir);
            return new KeyPair(pub, priv);
        }

        KeyPair pair = ed25519Service.generateKeyPair();
        Files.writeString(privPath, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        Files.writeString(pubPath, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        log.info("Generated and saved new server Ed25519 keys to {}", keysDir);

        return pair;
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
