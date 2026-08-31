package com.demo.upimesh.simulator;

import com.demo.upimesh.dtn.MeshPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates DTN mesh bundle propagation across virtual devices.
 */
@Service
public class MeshSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(MeshSimulatorService.class);

    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();

    public MeshSimulatorService() {
        seedDefaultDevices();
    }

    private void seedDefaultDevices() {
        devices.put("phone-alice",     new VirtualDevice("phone-alice",     false, 100, 200, 180));
        devices.put("phone-stranger1", new VirtualDevice("phone-stranger1", false, 240, 150, 180));
        devices.put("phone-stranger2", new VirtualDevice("phone-stranger2", false, 380, 250, 180));
        devices.put("phone-stranger3", new VirtualDevice("phone-stranger3", false, 520, 150, 180));
        devices.put("phone-bridge",    new VirtualDevice("phone-bridge",    true,  660, 200, 180));
    }

    public Collection<VirtualDevice> getDevices() {
        return devices.values();
    }

    public VirtualDevice getDevice(String id) {
        return devices.get(id);
    }

    public void inject(String senderDeviceId, MeshPacket packet) {
        VirtualDevice sender = devices.get(senderDeviceId);
        if (sender == null) throw new IllegalArgumentException("Unknown device: " + senderDeviceId);
        if (packet.getOriginDeviceId() == null || packet.getOriginDeviceId().equals("phone-unknown")) {
            packet.setOriginDeviceId(senderDeviceId);
        }
        sender.hold(packet);
        log.info("Packet {} injected at {} (TTL={})",
                packet.getPacketId().substring(0, Math.min(8, packet.getPacketId().length())), senderDeviceId, packet.getTtl());
    }

    public GossipResult gossipOnce() {
        int transfers = 0;
        List<VirtualDevice> deviceList = new ArrayList<>(devices.values());

        Map<String, List<MeshPacket>> snapshot = new HashMap<>();
        for (VirtualDevice d : deviceList) {
            snapshot.put(d.getDeviceId(), new ArrayList<>(d.getHeldPackets()));
        }

        for (VirtualDevice src : deviceList) {
            for (MeshPacket pkt : snapshot.get(src.getDeviceId())) {
                if (pkt.getTtl() <= 0) continue;
                for (VirtualDevice dst : deviceList) {
                    if (dst == src) continue;
                    if (dst.holds(pkt.getPacketId())) continue;

                    double dx = src.getX() - dst.getX();
                    double dy = src.getY() - dst.getY();
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > src.getRange()) continue;

                    MeshPacket copy = new MeshPacket();
                    copy.setVersion(pkt.getVersion());
                    copy.setPacketId(pkt.getPacketId());
                    copy.setKeyId(pkt.getKeyId());
                    copy.setOriginDeviceId(pkt.getOriginDeviceId());
                    copy.setTtl(pkt.getTtl() - 1);
                    copy.setCreatedAt(pkt.getCreatedAt());
                    copy.setCiphertext(pkt.getCiphertext());
                    dst.hold(copy);
                    transfers++;
                }
            }
        }

        log.info("Gossip round complete: {} packet transfers", transfers);
        return new GossipResult(transfers, snapshotMap());
    }

    public Map<String, Integer> snapshotMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (VirtualDevice d : devices.values()) {
            m.put(d.getDeviceId(), d.packetCount());
        }
        return m;
    }

    public List<BridgeUpload> collectBridgeUploads() {
        List<BridgeUpload> out = new ArrayList<>();
        for (VirtualDevice d : devices.values()) {
            if (!d.hasInternet()) continue;
            for (MeshPacket pkt : d.getHeldPackets()) {
                out.add(new BridgeUpload(d.getDeviceId(), pkt));
            }
        }
        return out;
    }

    public void resetMesh() {
        devices.values().forEach(VirtualDevice::clear);
    }

    public record GossipResult(int transfers, Map<String, Integer> deviceCounts) {}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet) {}
}
