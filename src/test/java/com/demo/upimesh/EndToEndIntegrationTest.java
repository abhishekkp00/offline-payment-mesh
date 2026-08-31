package com.demo.upimesh;

import com.demo.upimesh.integration.EndToEndIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EndToEndIntegrationTest {

    @Autowired private EndToEndIntegrationService endToEndService;

    @Test
    void testFullEndToEndScenarioAndAdversarialDemonstration() {
        EndToEndIntegrationService.EndToEndTelemetryResponse telemetry = endToEndService.executeFullScenario();

        assertNotNull(telemetry);
        assertNotNull(telemetry.transactionId());
        assertEquals("wlet-alice-demo", telemetry.walletId());
        assertEquals("alice@demo", telemetry.sender());
        assertEquals("bob@demo", telemetry.receiver());
        assertEquals("250.00", telemetry.amount());
        assertTrue(telemetry.dtnHops() > 0);
        assertTrue(telemetry.packetCopies() > 0);
        assertEquals("SPRAY_AND_WAIT", telemetry.routingAlgorithm());
        assertEquals("phone-bridge", telemetry.bridgeId());
        assertEquals("VERIFIED_VALID", telemetry.securityVerificationResult());
        assertEquals("CLAIMED_UNIQUE", telemetry.idempotencyResult());
        assertEquals("SETTLED", telemetry.settlementState());
        assertTrue(telemetry.settlementLatencyMs() >= 0);
        assertEquals("NO_CONFLICT", telemetry.conflictStatus());

        var scenarios = telemetry.demonstrationScenarios();
        assertNotNull(scenarios);
        assertEquals(7, scenarios.size());

        // 1. Duplicate Bridge Submissions
        assertEquals("DUPLICATE_DROPPED", scenarios.get("1_DUPLICATE_BRIDGE_SUBMISSIONS"));

        // 2. Packet Tampering
        assertTrue(scenarios.get("2_PACKET_TAMPERING").contains("decryption_or_aad_failed") || scenarios.get("2_PACKET_TAMPERING").contains("Tag mismatch"));

        // 3. Replay Attack
        assertTrue(scenarios.get("3_REPLAY_ATTACK").contains("REPLAY_ATTACK"));

        // 4. Forged Transaction
        assertTrue(scenarios.get("4_FORGED_TRANSACTION").contains("Invalid device transaction signature"));

        // 5. Bridge Failure
        assertTrue(scenarios.get("5_BRIDGE_FAILURE").contains("unauthorized_bridge"));

        // 6. Network Partition
        assertTrue(scenarios.get("6_NETWORK_PARTITION").contains("PARTITION_HEALED_DELIVERED"));

        // 7. Offline Overspending / Conflict
        assertTrue(scenarios.get("7_OFFLINE_OVERSPENDING_CONFLICT").contains("CONFLICTED"));
    }
}
