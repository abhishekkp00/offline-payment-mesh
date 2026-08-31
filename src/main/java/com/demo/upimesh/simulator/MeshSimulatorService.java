package com.demo.upimesh.simulator;

import com.demo.upimesh.dtn.MeshPacket;
import com.demo.upimesh.simulator.dtn.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulator service integrating discrete-event DTN routing algorithms with visual canvas state.
 */
@Service
public class MeshSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(MeshSimulatorService.class);

    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();
    private final EventDrivenDtnSimulator dtnSimulator = new EventDrivenDtnSimulator(42L);

    public MeshSimulatorService() {
        seedDefaultDevices();
    }

    private void seedDefaultDevices() {
        devices.put("phone-alice",     new VirtualDevice("phone-alice",     false, 100, 200, 180));
        devices.put("phone-stranger1", new VirtualDevice("phone-stranger1", false, 240, 150, 180));
        devices.put("phone-stranger2", new VirtualDevice("phone-stranger2", false, 380, 250, 180));
        devices.put("phone-stranger3", new VirtualDevice("phone-stranger3", false, 520, 150, 180));
        devices.put("phone-bridge",    new VirtualDevice("phone-bridge",    true,  660, 200, 180));

        for (VirtualDevice d : devices.values()) {
            MobileNode node = new MobileNode(d.getDeviceId(), d.hasInternet(), d.getX(), d.getY(), d.getRange(), 50);
            dtnSimulator.registerNode(node);
        }
    }

    public void setRoutingStrategy(String strategyName) {
        if ("SPRAY_AND_WAIT".equalsIgnoreCase(strategyName)) {
            dtnSimulator.setRoutingStrategy(new SprayAndWaitRoutingStrategy(8));
            log.info("Switched DTN routing strategy to SPRAY_AND_WAIT");
        } else {
            dtnSimulator.setRoutingStrategy(new EpidemicRoutingStrategy());
            log.info("Switched DTN routing strategy to EPIDEMIC");
        }
    }

    public String getRoutingStrategyName() {
        return dtnSimulator.getRoutingStrategy().getName();
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

        MobileNode node = dtnSimulator.getNode(senderDeviceId);
        if (node != null) {
            dtnSimulator.scheduleEvent(SimulationEvent.injectPacket(System.currentTimeMillis(), node, packet));
        }

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

        DtnRoutingStrategy strategy = dtnSimulator.getRoutingStrategy();

        for (VirtualDevice src : deviceList) {
            MobileNode srcNode = dtnSimulator.getNode(src.getDeviceId());
            for (VirtualDevice dst : deviceList) {
                if (dst == src) continue;

                double dx = src.getX() - dst.getX();
                double dy = src.getY() - dst.getY();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > src.getRange()) continue; // Out of wireless transmission range

                MobileNode dstNode = dtnSimulator.getNode(dst.getDeviceId());

                List<MeshPacket> candidates = snapshot.get(src.getDeviceId());
                List<MeshPacket> selected = strategy.selectPacketsToForward(srcNode, dstNode, candidates);

                for (MeshPacket pkt : selected) {
                    if (dst.holds(pkt.getPacketId())) continue;

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
                        if (dstNode != null) dstNode.hold(copy);
                        transfers++;
                    }
                }
            }
        }

        log.info("Gossip round complete: {} packet transfers using strategy {}", transfers, strategy.getName());
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
        for (MobileNode n : dtnSimulator.getNodes()) n.clear();
    }

    public DtnSimulationMetrics getDtnMetrics() {
        return dtnSimulator.getMetrics();
    }

    public record GossipResult(int transfers, Map<String, Integer> deviceCounts) {}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet) {}
}
