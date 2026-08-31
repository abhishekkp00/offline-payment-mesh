package com.demo.upimesh.simulator.dtn;

import com.demo.upimesh.dtn.MeshPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Epidemic DTN Routing Strategy.
 * Floods all missing packets with TTL > 0 to any encountered peer node.
 */
public class EpidemicRoutingStrategy implements DtnRoutingStrategy {

    @Override
    public String getName() {
        return "EPIDEMIC";
    }

    @Override
    public List<MeshPacket> selectPacketsToForward(MobileNode sender, MobileNode receiver, List<MeshPacket> candidatePackets) {
        List<MeshPacket> toForward = new ArrayList<>();
        if (candidatePackets == null || candidatePackets.isEmpty()) {
            return toForward;
        }

        for (MeshPacket pkt : candidatePackets) {
            if (pkt.getTtl() > 0 && !receiver.holds(pkt.getPacketId())) {
                toForward.add(pkt);
            }
        }
        return toForward;
    }
}
