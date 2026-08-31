package com.demo.upimesh;

import com.demo.upimesh.benchmark.BenchmarkRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BenchmarkExperimentTest {

    @Autowired private BenchmarkRunner benchmarkRunner;

    @Test
    void testBenchmarkExecution() {
        BenchmarkRunner.BenchmarkRequest request = new BenchmarkRunner.BenchmarkRequest(
                10,
                20,
                5.0,
                0.0,
                3,
                0,
                5,
                20,
                "EPIDEMIC",
                4
        );

        BenchmarkRunner.BenchmarkResult result = benchmarkRunner.runBenchmark(request);
        assertNotNull(result);
        assertEquals(20, result.totalTransactions());
        assertTrue(result.settledCount() > 0);
        assertTrue(result.deliveryRate() > 0.0);
        assertTrue(result.throughputTps() >= 0.0);
    }

    @Test
    void testAdversarialSuiteExecution() {
        BenchmarkRunner.AdversarialReport report = benchmarkRunner.runAdversarialSuite();
        assertNotNull(report);
        assertNotNull(report.scenarioResults());
        assertEquals(9, report.scenarioResults().size());

        assertTrue(report.scenarioResults().get("1_DUPLICATE_PACKET_FLOOD").contains("Passed"));
        assertTrue(report.scenarioResults().get("2_REPLAY_ATTACK").contains("Passed"));
        assertTrue(report.scenarioResults().get("3_CIPHERTEXT_TAMPERING").contains("Passed"));
        assertTrue(report.scenarioResults().get("4_METADATA_TAMPERING").contains("Passed"));
        assertTrue(report.scenarioResults().get("5_FORGED_SIGNATURE").contains("Passed"));
        assertTrue(report.scenarioResults().get("6_BRIDGE_FAILURE").contains("Passed"));
        assertTrue(report.scenarioResults().get("7_BACKEND_RESTART").contains("Passed"));
        assertTrue(report.scenarioResults().get("8_NETWORK_PARTITION").contains("Passed"));
        assertTrue(report.scenarioResults().get("9_CONFLICTING_OFFLINE_SPENDING").contains("Passed"));
    }
}
