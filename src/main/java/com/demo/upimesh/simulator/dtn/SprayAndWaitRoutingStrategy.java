package com.demo.upimesh.simulator.dtn;

import com.demo.upimesh.dtn.MeshPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spray-and-Wait DTN Routing Strategy.
 *
 * Spray Phase: Distributes binary copy allocations L > 1 across encountered peers.
 * Wait Phase: When L = 1, nodes only forward directly to Internet Bridge nodes.
 */
public class SprayAndWaitRoutingStrategy implements DtnRoutingStrategy {

    private final int defaultSprayCopies;
    private final Map<String, Integer> copyCounts = new ConcurrentHashMap<>();

    public SprayAndWaitRoutingStrategy() {
        this(8); // Default Spray Limit L = 8
    }

    public SprayAndWaitRoutingStrategy(int defaultSprayCopies) {
        this.defaultSprayCopies = defaultSprayCopies;
    }

    @Override
    public String getName() {
        return "SPRAY_AND_WAIT";
    }

    public int getCopyCount(String packetId) {
        return copyCounts.getOrDefault(packetId, defaultSprayCopies);
    }

    public void setCopyCount(String packetId, int count) {
        copyCounts.put(packetId, count);
    }

    @Override
    public List<MeshPacket> selectPacketsToForward(MobileNode sender, MobileNode receiver, List<MeshPacket> candidatePackets) {
        List<MeshPacket> toForward = new ArrayList<>();
        if (candidatePackets == null || candidatePackets.isEmpty()) {
            return toForward;
        }

        for (MeshPacket pkt : candidatePackets) {
            if (pkt.getTtl() <= 0 || receiver.holds(pkt.getPacketId())) {
                continue;
            }

            int currentCopies = copyCounts.getOrDefault(pkt.getPacketId(), defaultSprayCopies);

            if (currentCopies > 1) {
                // Spray Phase: Split copies binary-wise
                int sendCopies = currentCopies / 2;
                int keepCopies = currentCopies - sendCopies;
                copyCounts.put(pkt.getPacketId(), keepCopies);
                toForward.add(pkt);
            } else if (currentCopies == 1 && receiver.hasInternet()) {
                // Wait Phase: Only forward directly to an internet-capable Gateway Bridge
                toForward.add(pkt);
            }
        }
        return toForward;
    }
}
