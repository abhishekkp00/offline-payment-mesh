package com.demo.upimesh.dtn;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store-carry-forward bundle storage manager for a DTN mesh node.
 */
public class DtnBundleStore {

    private final Map<String, MeshPacket> bundles = new ConcurrentHashMap<>();

    public boolean store(MeshPacket packet) {
        if (packet == null || packet.getPacketId() == null) return false;
        return bundles.putIfAbsent(packet.getPacketId(), packet) == null;
    }

    public boolean contains(String packetId) {
        return bundles.containsKey(packetId);
    }

    public Collection<MeshPacket> getBundles() {
        return Collections.unmodifiableCollection(bundles.values());
    }

    public int count() {
        return bundles.size();
    }

    public void clear() {
        bundles.clear();
    }
}
