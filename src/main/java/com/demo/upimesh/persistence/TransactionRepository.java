package com.demo.upimesh.persistence;

import com.demo.upimesh.protocol.TransactionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByPacketHash(String packetHash);
    List<Transaction> findByStateAndUpdatedAtBefore(TransactionState state, Instant cutoff);
    List<Transaction> findTop20ByOrderByIdDesc();
}
