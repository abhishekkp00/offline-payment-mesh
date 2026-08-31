package com.demo.upimesh.simulator.dtn;

import com.demo.upimesh.dtn.MeshPacket;

import java.util.Objects;

/**
 * Event-Driven Discrete Simulation Queue Event.
 */
public class SimulationEvent implements Comparable<SimulationEvent> {

    public enum EventType {
        CONTACT_START,
        CONTACT_END,
        INJECT_PACKET,
        NODE_FAILURE,
        NODE_RECOVERY
    }

    private final long timestampMs;
    private final EventType type;
    private final MobileNode nodeA;
    private final MobileNode nodeB;
    private final MeshPacket packet;

    public SimulationEvent(long timestampMs, EventType type, MobileNode nodeA, MobileNode nodeB, MeshPacket packet) {
        this.timestampMs = timestampMs;
        this.type = Objects.requireNonNull(type, "type required");
        this.nodeA = nodeA;
        this.nodeB = nodeB;
        this.packet = packet;
    }

    public static SimulationEvent contactStart(long timestampMs, MobileNode nodeA, MobileNode nodeB) {
        return new SimulationEvent(timestampMs, EventType.CONTACT_START, nodeA, nodeB, null);
    }

    public static SimulationEvent contactEnd(long timestampMs, MobileNode nodeA, MobileNode nodeB) {
        return new SimulationEvent(timestampMs, EventType.CONTACT_END, nodeA, nodeB, null);
    }

    public static SimulationEvent injectPacket(long timestampMs, MobileNode node, MeshPacket packet) {
        return new SimulationEvent(timestampMs, EventType.INJECT_PACKET, node, null, packet);
    }

    public static SimulationEvent nodeFailure(long timestampMs, MobileNode node) {
        return new SimulationEvent(timestampMs, EventType.NODE_FAILURE, node, null, null);
    }

    public static SimulationEvent nodeRecovery(long timestampMs, MobileNode node) {
        return new SimulationEvent(timestampMs, EventType.NODE_RECOVERY, node, null, null);
    }

    public long getTimestampMs() { return timestampMs; }
    public EventType getType() { return type; }
    public MobileNode getNodeA() { return nodeA; }
    public MobileNode getNodeB() { return nodeB; }
    public MeshPacket getPacket() { return packet; }

    @Override
    public int compareTo(SimulationEvent other) {
        return Long.compare(this.timestampMs, other.timestampMs);
    }
}
