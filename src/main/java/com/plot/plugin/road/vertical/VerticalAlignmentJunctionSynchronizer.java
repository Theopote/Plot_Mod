package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;

/** Promotes designed road endpoint elevations to shared at-grade junction constraints. */
public final class VerticalAlignmentJunctionSynchronizer {
    private VerticalAlignmentJunctionSynchronizer() { }

    public static int synchronize(RoadNetwork network, Road road) {
        if (network == null || road == null || road.getVerticalAlignment() == null
                || road.getVerticalAlignment().pviCount() < 2
                || !RoadStationing.isStationable(network, road)) {
            return 0;
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        int changed = 0;
        changed += synchronizeNode(
            network.getNode(RoadStationing.chainEntryNodeId(network, road).orElse(null)),
            alignment.getPvis().getFirst().getElevation());
        changed += synchronizeNode(
            network.getNode(RoadStationing.chainExitNodeId(network, road).orElse(null)),
            alignment.getPvis().getLast().getElevation());
        return changed;
    }

    private static int synchronizeNode(RoadNode node, double elevation) {
        if (node == null || !node.isJunction() || node.isGradeSeparated()) {
            return 0;
        }
        Double previous = node.getManualElevation();
        node.setManualElevation(elevation);
        return previous == null || Math.abs(previous - node.getManualElevation()) > 1e-6 ? 1 : 0;
    }
}
