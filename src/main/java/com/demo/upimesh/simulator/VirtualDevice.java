package com.demo.upimesh.simulator;

import com.demo.upimesh.dtn.DtnBundleStore;
import com.demo.upimesh.dtn.MeshPacket;

import java.util.Collection;

/**
 * A simulated mobile phone node in the mesh.
 */
public class VirtualDevice {

    private final String deviceId;
    private final boolean hasInternet;
    private final DtnBundleStore bundleStore = new DtnBundleStore();
    private double x;
    private double y;
    private double range = 180.0;

    public VirtualDevice(String deviceId, boolean hasInternet) {
        this.deviceId = deviceId;
        this.hasInternet = hasInternet;
    }

    public VirtualDevice(String deviceId, boolean hasInternet, double x, double y, double range) {
        this.deviceId = deviceId;
        this.hasInternet = hasInternet;
        this.x = x;
        this.y = y;
        this.range = range;
    }

    public String getDeviceId() { return deviceId; }
    public boolean hasInternet() { return hasInternet; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getRange() { return range; }
    public void setRange(double range) { this.range = range; }

    public boolean hold(MeshPacket packet) {
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
