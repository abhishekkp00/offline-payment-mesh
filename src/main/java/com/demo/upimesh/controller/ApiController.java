package com.demo.upimesh.controller;

import com.demo.upimesh.benchmark.BenchmarkRunner;
import com.demo.upimesh.bridge.BridgeIngestionService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.idempotency.IdempotencyService;
import com.demo.upimesh.integration.EndToEndIntegrationService;
import com.demo.upimesh.observability.AuditLogger;
import com.demo.upimesh.persistence.Account;
import com.demo.upimesh.persistence.AccountRepository;
import com.demo.upimesh.persistence.Transaction;
import com.demo.upimesh.persistence.TransactionRepository;
import com.demo.upimesh.simulator.DemoService;
import com.demo.upimesh.simulator.MeshSimulatorService;
import com.demo.upimesh.simulator.VirtualDevice;
import com.demo.upimesh.wallet.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Public REST endpoints surface.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired private ServerKeyHolder serverKey;
    @Autowired private DemoService demo;
    @Autowired private MeshSimulatorService mesh;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private IdempotencyService idempotency;
    @Autowired private WalletService walletService;
    @Autowired private AuditLogger auditLogger;
    @Autowired private BenchmarkRunner benchmarkRunner;
    @Autowired private EndToEndIntegrationService e2eService;

    // ------------------------------------------------------------------ key

    @GetMapping("/server-key")
    public Map<String, String> getServerPublicKey() {
        return Map.of(
                "publicKey", serverKey.getPublicKeyBase64(),
                "algorithm", "RSA-2048 / OAEP-SHA256",
                "hybridScheme", "RSA-OAEP encrypts an AES-256-GCM session key",
                "protocolVersion", "1.0.0"
        );
    }

    // ---------------------------------------------------------------- demo

    @PostMapping("/demo/send")
    public ResponseEntity<?> demoSend(@RequestBody DemoSendRequest req) throws Exception {
        MeshPacket packet = demo.createPacket(
                req.senderVpa, req.receiverVpa, req.amount, req.pin,
                req.ttl == null ? 5 : req.ttl);

        String startDevice = req.startDevice == null ? "phone-alice" : req.startDevice;
        mesh.inject(startDevice, packet);

        return ResponseEntity.ok(Map.of(
                "packetId", packet.getPacketId(),
                "ciphertextPreview", packet.getCiphertext().substring(0, Math.min(64, packet.getCiphertext().length())) + "...",
                "ttl", packet.getTtl(),
                "injectedAt", startDevice,
                "protocolVersion", packet.getVersion()
        ));
    }

    public static class DemoSendRequest {
        public String senderVpa;
        public String receiverVpa;
        public BigDecimal amount;
        public String pin;
        public Integer ttl;
        public String startDevice;
    }

    // -------------------------------------------------------------- end-to-end

    @PostMapping("/demo/end-to-end-simulation")
    public ResponseEntity<EndToEndIntegrationService.EndToEndTelemetryResponse> runEndToEndSimulation() {
        var response = e2eService.executeFullScenario();
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------- mesh sim

    @GetMapping("/mesh/strategy")
    public Map<String, String> getStrategy() {
        return Map.of("activeStrategy", mesh.getRoutingStrategyName());
    }

    @PostMapping("/mesh/strategy")
    public Map<String, String> setStrategy(@RequestBody Map<String, String> body) {
        String strategyName = body.get("strategy");
        mesh.setRoutingStrategy(strategyName);
        return Map.of("activeStrategy", mesh.getRoutingStrategyName(), "status", "success");
    }

    @GetMapping("/mesh/dtn-metrics")
    public Map<String, Object> getDtnMetrics() {
        return mesh.getDtnMetrics().getMetricsSummary();
    }

    @GetMapping("/mesh/state")
    public Map<String, Object> meshState() {
        List<Map<String, Object>> deviceData = new ArrayList<>();
        for (VirtualDevice d : mesh.getDevices()) {
            deviceData.add(Map.of(
                    "deviceId", d.getDeviceId(),
                    "hasInternet", d.hasInternet(),
                    "packetCount", d.packetCount(),
                    "packetIds", d.getHeldPackets().stream()
                            .map(p -> p.getPacketId().substring(0, Math.min(8, p.getPacketId().length())))
                            .toList(),
                    "x", d.getX(),
                    "y", d.getY(),
                    "range", d.getRange()
            ));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("devices", deviceData);
        result.put("idempotencyCacheSize", idempotency.size());
        result.put("activeStrategy", mesh.getRoutingStrategyName());
        result.put("metrics", auditLogger.getMetricsSnapshot());
        result.put("dtnMetrics", mesh.getDtnMetrics().getMetricsSummary());
        return result;
    }

    @PostMapping("/mesh/update-positions")
    public Map<String, Object> updatePositions(@RequestBody List<UpdatePositionRequest> reqs) {
        for (UpdatePositionRequest req : reqs) {
            VirtualDevice d = mesh.getDevice(req.deviceId);
            if (d != null) {
                if (req.x != null) d.setX(req.x);
                if (req.y != null) d.setY(req.y);
                if (req.range != null) d.setRange(req.range);
            }
        }
        return Map.of("status", "success");
    }

    public static class UpdatePositionRequest {
        public String deviceId;
        public Double x;
        public Double y;
        public Double range;
    }

    @PostMapping("/mesh/gossip")
    public Map<String, Object> meshGossip() {
        MeshSimulatorService.GossipResult r = mesh.gossipOnce();
        return Map.of(
                "transfers", r.transfers(),
                "deviceCounts", r.deviceCounts(),
                "activeStrategy", mesh.getRoutingStrategyName()
        );
    }

    @PostMapping("/mesh/flush")
    public Map<String, Object> meshFlush() {
        List<MeshSimulatorService.BridgeUpload> uploads = mesh.collectBridgeUploads();

        List<Map<String, Object>> results = new ArrayList<>();
        uploads.parallelStream().forEach(up -> {
            BridgeIngestionService.IngestResult r =
                    bridge.ingest(up.packet(), up.bridgeNodeId(), Math.max(1, 5 - up.packet().getTtl()));
            synchronized (results) {
                results.add(Map.of(
                        "bridgeNode", up.bridgeNodeId(),
                        "packetId", up.packet().getPacketId().substring(0, Math.min(8, up.packet().getPacketId().length())),
                        "outcome", r.outcome(),
                        "reason", r.reason() == null ? "" : r.reason(),
                        "transactionId", r.transactionId() == null ? -1 : r.transactionId()
                ));
            }
        });

        return Map.of(
                "uploadsAttempted", uploads.size(),
                "results", results
        );
    }

    @PostMapping("/mesh/reset")
    public Map<String, Object> meshReset() {
        mesh.resetMesh();
        idempotency.clear();
        return Map.of("status", "mesh and idempotency cache cleared");
    }

    // -------------------------------------------------------------- bridge

    @PostMapping("/bridge/ingest")
    public ResponseEntity<?> ingest(
            @RequestBody MeshPacket packet,
            @RequestHeader(value = "X-Bridge-Node-Id", defaultValue = "unknown") String bridgeNodeId,
            @RequestHeader(value = "X-Hop-Count", defaultValue = "0") int hopCount) {

        BridgeIngestionService.IngestResult r = bridge.ingest(packet, bridgeNodeId, hopCount);
        return ResponseEntity.ok(r);
    }

    // ------------------------------------------------------------- accounts

    @GetMapping("/accounts")
    public List<Account> listAccounts() {
        return accountRepo.findAll();
    }

    @GetMapping("/transactions")
    public List<Transaction> listTransactions() {
        return txRepo.findTop20ByOrderByIdDesc();
    }

    // ------------------------------------------------------------- benchmark

    @PostMapping("/benchmark/run")
    public ResponseEntity<BenchmarkRunner.BenchmarkResult> runBenchmark(@RequestBody BenchmarkRunner.BenchmarkRequest request) {
        BenchmarkRunner.BenchmarkResult result = benchmarkRunner.runBenchmark(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/benchmark/adversarial")
    public ResponseEntity<BenchmarkRunner.AdversarialReport> runAdversarialSuite() {
        BenchmarkRunner.AdversarialReport report = benchmarkRunner.runAdversarialSuite();
        return ResponseEntity.ok(report);
    }
}
