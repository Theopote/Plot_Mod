package com.plot.plugin.road.overlay;

import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.List;

/** 从路网合成交叉点叠加层条目。 */
public final class RoadJunctionOverlayController {
    private RoadJunctionOverlayController() {
    }

    public static List<RoadJunctionOverlayEntry> snapshot(
            RoadNetwork network,
            RoadNetworkBuilder builder,
            String selectedNodeId) {
        if (network == null || builder == null) {
            return List.of();
        }
        List<RoadJunctionOverlayEntry> entries = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getDegree() < 2) {
                continue;
            }
            if (!node.isJunction() && !RoadGraphQueries.isSimpleCrossing(node, network)) {
                continue;
            }
            boolean selected = node.getId().equals(selectedNodeId);
            RoadJunctionOverlayKind kind = resolveKind(node, network, builder, selected);
            entries.add(new RoadJunctionOverlayEntry(
                node.getId(),
                node.getPosition(),
                kind,
                selected));
        }
        return entries;
    }

    private static RoadJunctionOverlayKind resolveKind(
            RoadNode node,
            RoadNetwork network,
            RoadNetworkBuilder builder,
            boolean selected) {
        if (selected) {
            return RoadJunctionOverlayKind.SELECTED;
        }
        if (node.isGradeSeparated()) {
            return RoadJunctionOverlayKind.GRADE_SEPARATED;
        }
        RoadNetworkBuilder.JunctionType type = builder.classify(node);
        if (type == RoadNetworkBuilder.JunctionType.COMPLEX) {
            return RoadJunctionOverlayKind.COMPLEX;
        }
        if (node.getDegree() >= 3) {
            return RoadJunctionOverlayKind.AT_GRADE;
        }
        return RoadJunctionOverlayKind.AT_GRADE;
    }
}
