package com.demo.upimesh.simulator;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.persistence.Account;
import com.demo.upimesh.persistence.AccountRepository;
import com.demo.upimesh.protocol.PaymentInstruction;
import com.demo.upimesh.wallet.WalletService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service seeding initial accounts/wallets and creating demo packets.
 */
@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    @Autowired private AccountRepository accounts;
    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;
    @Autowired private WalletService walletService;

    @PostConstruct
    public void seedData() {
        if (accounts.count() == 0) {
            accounts.save(new Account("alice@demo", "Alice", new BigDecimal("5000.00")));
            accounts.save(new Account("bob@demo",   "Bob",   new BigDecimal("1000.00")));
            accounts.save(new Account("carol@demo", "Carol", new BigDecimal("2500.00")));
            accounts.save(new Account("dave@demo",  "Dave",  new BigDecimal("500.00")));

            walletService.registerWallet("alice@demo", new BigDecimal("5000.00"));
            walletService.registerWallet("bob@demo",   new BigDecimal("1000.00"));
            walletService.registerWallet("carol@demo", new BigDecimal("2500.00"));
            walletService.registerWallet("dave@demo",  new BigDecimal("500.00"));

            log.info("Seeded 4 demo accounts and pre-funded wallets");
        }
    }

    public MeshPacket createPacket(String senderVpa, String receiverVpa, BigDecimal amount,
                                  String pin, int ttl) throws Exception {

        PaymentInstruction instruction = walletService.prepareOfflineInstruction(
                senderVpa, receiverVpa, amount, pin);

        String ciphertext = crypto.encrypt(instruction, serverKey.getPublicKey());

        MeshPacket pkt = new MeshPacket();
        pkt.setVersion("1.0.0");
        pkt.setPacketId(UUID.randomUUID().toString());
        pkt.setKeyId("key-server-rsa-2048");
        pkt.setOriginDeviceId("phone-" + senderVpa.split("@")[0]);
        pkt.setTtl(ttl);
        pkt.setCreatedAt(System.currentTimeMillis());
        pkt.setCiphertext(ciphertext);
        return pkt;
    }
}
