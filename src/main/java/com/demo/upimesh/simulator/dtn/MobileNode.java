package com.demo.upimesh.simulator.dtn;

import com.demo.upimesh.dtn.DtnBundleStore;
import com.demo.upimesh.dtn.MeshPacket;

import java.util.Collection;

/**
 * Event-Driven DTN Simulator Mobile Node.
 */
public class MobileNode {

    public enum NodeStatus { ACTIVE, FAILED, OUT_OF_POWER }

    private final String nodeId;
    private boolean hasInternet;
    private double x;
    private double y;
    private double range = 180.0;
    private NodeStatus status = NodeStatus.ACTIVE;
    private int maxBufferPackets = 50; // Buffer capacity limit
    private final DtnBundleStore bundleStore = new DtnBundleStore();

    public MobileNode(String nodeId, boolean hasInternet) {
        this.nodeId = nodeId;
        this.hasInternet = hasInternet;
    }

    public MobileNode(String nodeId, boolean hasInternet, double x, double y, double range, int maxBufferPackets) {
        this.nodeId = nodeId;
        this.hasInternet = hasInternet;
        this.x = x;
        this.y = y;
        this.range = range;
        this.maxBufferPackets = maxBufferPackets;
    }

    public String getNodeId() { return nodeId; }

    public boolean hasInternet() { return hasInternet; }
    public void setHasInternet(boolean hasInternet) { this.hasInternet = hasInternet; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getRange() { return range; }
    public void setRange(double range) { this.range = range; }

    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }

    public int getMaxBufferPackets() { return maxBufferPackets; }
    public void setMaxBufferPackets(int maxBufferPackets) { this.maxBufferPackets = maxBufferPackets; }

    public boolean isBufferFull() {
        return bundleStore.count() >= maxBufferPackets;
    }

    public double getBufferUtilization() {
        if (maxBufferPackets <= 0) return 0.0;
        return ((double) bundleStore.count() / maxBufferPackets) * 100.0;
    }

    public boolean hold(MeshPacket packet) {
        if (status != NodeStatus.ACTIVE) return false;
        if (isBufferFull()) return false; // Buffer overflow drop
        return bundleStore.store(packet);
    }

    public Collection<MeshPacket> getHeldPackets() {
        return bundleStore.getBundles();
    }

    public boolean holds(String packetId) {
        return bundleStore.contains(packetId);
    }

    public int packetCount() {
        return bundleStore.count();
    }

    public void clear() {
        bundleStore.clear();
    }
}
