package com.demo.upimesh.simulator.dtn;

import com.demo.upimesh.dtn.MeshPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-Driven Discrete Event Simulator (DES) for Delay/Disruption Tolerant Networks.
 */
public class EventDrivenDtnSimulator {

    private static final Logger log = LoggerFactory.getLogger(EventDrivenDtnSimulator.class);

    private final PriorityQueue<SimulationEvent> eventQueue = new PriorityQueue<>();
    private final Map<String, MobileNode> nodes = new ConcurrentHashMap<>();
    private final DtnSimulationMetrics metrics = new DtnSimulationMetrics();

    private DtnRoutingStrategy routingStrategy = new EpidemicRoutingStrategy();
    private Random random = new Random(42L); // Fixed seed for 100% deterministic reproducibility

    private double packetLossProbability = 0.02; // 2% packet loss rate
    private double connectionFailureRate = 0.01; // 1% connection drop rate
    private long currentClockMs = 0L;

    public EventDrivenDtnSimulator() {}

    public EventDrivenDtnSimulator(long randomSeed) {
        this.random = new Random(randomSeed);
    }

    public void setRoutingStrategy(DtnRoutingStrategy routingStrategy) {
        this.routingStrategy = Objects.requireNonNull(routingStrategy, "routingStrategy required");
    }

    public DtnRoutingStrategy getRoutingStrategy() {
        return routingStrategy;
    }

    public void registerNode(MobileNode node) {
        nodes.put(node.getNodeId(), node);
    }

    public MobileNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public Collection<MobileNode> getNodes() {
        return nodes.values();
    }

    public void setPacketLossProbability(double prob) {
        this.packetLossProbability = prob;
    }

    public void setConnectionFailureRate(double rate) {
        this.connectionFailureRate = rate;
    }

    public void scheduleEvent(SimulationEvent event) {
        if (event != null) {
            eventQueue.add(event);
        }
    }

    public boolean stepNextEvent() {
        if (eventQueue.isEmpty()) return false;

        SimulationEvent event = eventQueue.poll();
        this.currentClockMs = event.getTimestampMs();

        switch (event.getType()) {
            case CONTACT_START -> handleContactStart(event);
            case CONTACT_END -> log.debug("Contact ended between {} and {} at {}ms",
                    event.getNodeA().getNodeId(), event.getNodeB().getNodeId(), currentClockMs);
            case INJECT_PACKET -> handleInjectPacket(event);
            case NODE_FAILURE -> event.getNodeA().setStatus(MobileNode.NodeStatus.FAILED);
            case NODE_RECOVERY -> event.getNodeA().setStatus(MobileNode.NodeStatus.ACTIVE);
        }
        return true;
    }

    public void runSimulationUntil(long targetTimeMs) {
        while (!eventQueue.isEmpty() && eventQueue.peek().getTimestampMs() <= targetTimeMs) {
            stepNextEvent();
        }
    }

    private void handleInjectPacket(SimulationEvent event) {
        MobileNode node = event.getNodeA();
        MeshPacket packet = event.getPacket();
        if (node != null && packet != null) {
            node.hold(packet);
            metrics.recordPacketGenerated();
            metrics.updateBufferUtilization(node.getNodeId(), node.getBufferUtilization());
            log.info("Packet {} injected at {} (TTL={})", packet.getPacketId(), node.getNodeId(), packet.getTtl());
        }
    }

    private void handleContactStart(SimulationEvent event) {
        MobileNode a = event.getNodeA();
        MobileNode b = event.getNodeB();

        if (a == null || b == null) return;
        if (a.getStatus() != MobileNode.NodeStatus.ACTIVE || b.getStatus() != MobileNode.NodeStatus.ACTIVE) return;

        // Probabilistic Connection Failure check
        if (random.nextDouble() < connectionFailureRate) {
            log.warn("Connection failure between {} and {}", a.getNodeId(), b.getNodeId());
            return;
        }

        // Bilateral DTN bundle forwarding
        forwardBundles(a, b);
        forwardBundles(b, a);

        metrics.updateBufferUtilization(a.getNodeId(), a.getBufferUtilization());
        metrics.updateBufferUtilization(b.getNodeId(), b.getBufferUtilization());
    }

    private void forwardBundles(MobileNode src, MobileNode dst) {
        List<MeshPacket> candidates = new ArrayList<>(src.getHeldPackets());
        List<MeshPacket> selected = routingStrategy.selectPacketsToForward(src, dst, candidates);

        for (MeshPacket pkt : selected) {
            if (dst.holds(pkt.getPacketId())) {
                metrics.recordDuplicateTransmission();
                continue;
            }

            // Probabilistic Packet Loss check
            if (random.nextDouble() < packetLossProbability) {
                log.warn("Packet {} lost in transmission from {} to {}", pkt.getPacketId(), src.getNodeId(), dst.getNodeId());
                metrics.recordPacketDrop();
                continue;
            }

            // Buffer Overflow check
            if (dst.isBufferFull()) {
                log.warn("Buffer full at node {}, dropping packet {}", dst.getNodeId(), pkt.getPacketId());
                metrics.recordPacketDrop();
                continue;
            }

            MeshPacket copy = new MeshPacket();
            copy.setVersion(pkt.getVersion());
            copy.setPacketId(pkt.getPacketId());
            copy.setTransactionId(pkt.getTransactionId());
            copy.setWalletId(pkt.getWalletId());
            copy.setKeyId(pkt.getKeyId());
            copy.setOriginDeviceId(pkt.getOriginDeviceId());
            copy.setTtl(pkt.getTtl() - 1);
            copy.setHopCount(pkt.getHopCount() + 1);
            copy.setCreatedAt(pkt.getCreatedAt());
            copy.setCiphertext(pkt.getCiphertext());

            boolean stored = dst.hold(copy);
            if (stored) {
                metrics.recordPacketCopy();
                if (dst.hasInternet()) {
                    long latency = currentClockMs - (pkt.getCreatedAt() == null ? currentClockMs : pkt.getCreatedAt());
                    metrics.recordPacketDelivered(Math.max(0, latency), copy.getHopCount());
                }
            }
        }
    }

    public DtnSimulationMetrics getMetrics() {
        return metrics;
    }

    public long getCurrentClockMs() {
        return currentClockMs;
    }
}
