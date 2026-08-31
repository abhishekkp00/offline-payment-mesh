package com.demo.upimesh;

import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.simulator.dtn.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DtnSimulatorDeterministicTest {

    private EventDrivenDtnSimulator simulator;
    private MobileNode aliceNode;
    private MobileNode relayNode;
    private MobileNode bridgeNode;

    @BeforeEach
    void setUp() {
        simulator = new EventDrivenDtnSimulator(42L); // Fixed seed for 100% deterministic reproducibility
        aliceNode = new MobileNode("node-alice", false, 100, 100, 150, 10);
        relayNode = new MobileNode("node-relay", false, 200, 100, 150, 10);
        bridgeNode = new MobileNode("node-bridge", true, 300, 100, 150, 10);

        simulator.registerNode(aliceNode);
        simulator.registerNode(relayNode);
        simulator.registerNode(bridgeNode);
    }

    @Test
    void testEpidemicRoutingDeterministicRun() {
        simulator.setRoutingStrategy(new EpidemicRoutingStrategy());
        assertEquals("EPIDEMIC", simulator.getRoutingStrategy().getName());

        MeshPacket packet = new MeshPacket();
        packet.setPacketId("pkt-deterministic-1");
        packet.setTransactionId("tx-det-1");
        packet.setWalletId("wlet-alice");
        packet.setTtl(5);

        simulator.scheduleEvent(SimulationEvent.injectPacket(0, aliceNode, packet));
        simulator.scheduleEvent(SimulationEvent.contactStart(100, aliceNode, relayNode));
        simulator.scheduleEvent(SimulationEvent.contactStart(200, relayNode, bridgeNode));

        simulator.runSimulationUntil(300);

        assertTrue(aliceNode.holds("pkt-deterministic-1"));
        assertTrue(relayNode.holds("pkt-deterministic-1"));
        assertTrue(bridgeNode.holds("pkt-deterministic-1"));

        DtnSimulationMetrics metrics = simulator.getMetrics();
        assertEquals(1, metrics.getMetricsSummary().get("packetsGenerated"));
        assertEquals(1, metrics.getMetricsSummary().get("packetsDelivered"));
        assertEquals(2, metrics.getMetricsSummary().get("packetCopies"));
    }

    @Test
    void testSprayAndWaitRoutingBinaryCopySplitting() {
        SprayAndWaitRoutingStrategy strategy = new SprayAndWaitRoutingStrategy(8);
        simulator.setRoutingStrategy(strategy);
        assertEquals("SPRAY_AND_WAIT", strategy.getName());

        MeshPacket packet = new MeshPacket();
        packet.setPacketId("pkt-spray-1");
        packet.setTtl(5);

        // Initial copies L = 8
        assertEquals(8, strategy.getCopyCount("pkt-spray-1"));

        // First contact: Alice (L=8) sprays 4 copies to Relay (L=4), keeps 4
        List<MeshPacket> selected1 = strategy.selectPacketsToForward(aliceNode, relayNode, List.of(packet));
        assertEquals(1, selected1.size());
        assertEquals(4, strategy.getCopyCount("pkt-spray-1"));

        // In Wait Phase (L=1): Cannot forward to non-bridge relay, can only forward to internet bridge
        strategy.setCopyCount("pkt-spray-1", 1);
        List<MeshPacket> selectedToNonBridge = strategy.selectPacketsToForward(aliceNode, relayNode, List.of(packet));
        assertTrue(selectedToNonBridge.isEmpty(), "In Wait phase (L=1), node cannot spray to non-bridge relay");

        List<MeshPacket> selectedToBridge = strategy.selectPacketsToForward(aliceNode, bridgeNode, List.of(packet));
        assertEquals(1, selectedToBridge.size(), "In Wait phase (L=1), node can forward directly to Internet Bridge");
    }

    @Test
    void testBufferOverflowDrop() {
        MobileNode smallBufferNode = new MobileNode("node-small", false, 100, 100, 150, 2); // Max capacity 2
        simulator.registerNode(smallBufferNode);

        MeshPacket p1 = new MeshPacket(); p1.setPacketId("p1");
        MeshPacket p2 = new MeshPacket(); p2.setPacketId("p2");
        MeshPacket p3 = new MeshPacket(); p3.setPacketId("p3");

        assertTrue(smallBufferNode.hold(p1));
        assertTrue(smallBufferNode.hold(p2));
        assertFalse(smallBufferNode.hold(p3), "Buffer overflow should reject third packet");
        assertTrue(smallBufferNode.isBufferFull());
    }
}
