package com.demo.upimesh.simulator.dtn;

import com.demo.upimesh.dtn.MeshPacket;

import java.util.List;

/**
 * Pluggable DTN Routing Algorithm Strategy Interface.
 */
public interface DtnRoutingStrategy {

    /**
     * Unique identifier for the routing algorithm (e.g. "EPIDEMIC", "SPRAY_AND_WAIT").
     */
    String getName();

    /**
     * Selects which candidate bundles should be forwarded from sender to receiver during a contact event.
     */
    List<MeshPacket> selectPacketsToForward(MobileNode sender, MobileNode receiver, List<MeshPacket> candidatePackets);
}
