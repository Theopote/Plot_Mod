package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.vertical.RoadVerticalAlignmentResolver;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * 从道路设计纵断面直接读取节点处目标标高（不经坡度求解器）。
 */
public final class VerticalAlignmentEndpointHeight {

    private VerticalAlignmentEndpointHeight() {
    }

    public static OptionalInt atNode(RoadNetwork network, RoadEdge edge, RoadNode node) {
        return atNode(network, edge, node, null);
    }

    public static OptionalInt atNode(RoadNetwork network, RoadEdge edge, RoadNode node, RoadSystemConfig config) {
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
        if (!RoadStationing.isStationable(network, road)) {
            return OptionalInt.empty();
        }
        OptionalDouble station = RoadStationing.stationAtNode(network, road, edge, node.getId());
        if (station.isEmpty()) {
            return OptionalInt.empty();
        }
        double grade = config != null ? road.getEffectiveMaxSlope(config)
            : road.getMaxSlope() != null ? road.getMaxSlope() : 10.0;
        var alignment = RoadVerticalAlignmentResolver.resolve(network, road, grade);
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) return OptionalInt.empty();
        DesignElevationSource source = new DesignElevationSource(
            alignment,
            RoadStationing.orientedSegment(network, road, edge.getId()).orElseThrow(),
            edge.getLength());
        return OptionalInt.of(source.elevationAtChainage(station.getAsDouble()));
    }
}
