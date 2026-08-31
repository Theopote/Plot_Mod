package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;

import java.util.OptionalInt;

/**
 * 从道路设计纵断面直接读取节点处目标标高（不经坡度求解器）。
 */
public final class VerticalAlignmentEndpointHeight {

    private VerticalAlignmentEndpointHeight() {
    }

    public static OptionalInt atNode(RoadNetwork network, RoadEdge edge, RoadNode node) {
        if (network == null || edge == null || node == null) {
            return OptionalInt.empty();
        }
        String roadId = edge.getRoadId();
        if (roadId == null) {
            return OptionalInt.empty();
        }
        Road road = network.getRoad(roadId);
        if (!VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road)) {
            return OptionalInt.empty();
        }
        Double localDistance = localDistanceOnEdge(edge, node.getId());
        if (localDistance == null || !RoadStationing.isStationable(network, road)) {
            return OptionalInt.empty();
        }
        double segmentStart = RoadStationing.segmentStartStation(network, road, edge.getId());
        if (segmentStart < 0.0) {
            return OptionalInt.empty();
        }
        DesignElevationSource source = new DesignElevationSource(
            road.getVerticalAlignment(),
            segmentStart,
            edge.getLength(),
            edge.getLength());
        return OptionalInt.of(source.elevationAtLocalDistance(localDistance));
    }

    private static Double localDistanceOnEdge(RoadEdge edge, String nodeId) {
        if (edge.getStartNodeId().equals(nodeId)) {
            return 0.0;
        }
        if (edge.getEndNodeId().equals(nodeId)) {
            return edge.getLength();
        }
        return null;
    }
}
