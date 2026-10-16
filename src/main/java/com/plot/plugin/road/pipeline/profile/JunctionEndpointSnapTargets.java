package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import java.util.List;
import java.util.Map;

/**
 * Resolves endpoint snap elevations: at-grade junctions use unified network node Y;
 * elevated roads keep their solved deck height at grade-separated nodes.
 */
public final class JunctionEndpointSnapTargets {
    private JunctionEndpointSnapTargets() {
    }

    /**
     * Elevated deck edges must not endpoint-snap at grade-separated nodes.
     */
    public static boolean skipSnapForElevatedRoad(
            RoadNode node,
            RoadEdge edge,
            RoadNetwork network,
            ElevatedRoadIdResolver elevatedRoadIdResolver) {
        if (node == null || edge == null) {
            return false;
        }
        if (node.isStackGradeSeparated()) {
            return node.isStackUpperEdge(edge.getId());
        }
        if (!node.isGradeSeparated()) {
            return false;
        }
        String elevatedRoadId = elevatedRoadIdResolver != null
            ? elevatedRoadIdResolver.resolve(node, network)
            : node.getElevatedRoadId();
        return elevatedRoadId != null && elevatedRoadId.equals(edge.getRoadId());
    }

    public static Integer snapTargetY(
            RoadNode node,
            RoadEdge edge,
            RoadNetwork network,
            Map<String, Integer> networkNodeElevations,
            List<SegmentHeightInfo> heightInfos,
            boolean start,
            ElevatedRoadIdResolver elevatedRoadIdResolver) {
        if (node == null) {
            return null;
        }
        Integer networkY = networkNodeElevations != null ? networkNodeElevations.get(node.getId()) : null;
        Integer profileY = profileEndpointY(heightInfos, start);
        if (node.isStackGradeSeparated()) {
            if (edge != null && node.isStackUpperEdge(edge.getId())) {
                return profileY != null ? profileY : networkY;
            }
            return networkY != null ? networkY : profileY;
        }
        if (node.isGradeSeparated()) {
            String elevatedRoadId = elevatedRoadIdResolver != null
                ? elevatedRoadIdResolver.resolve(node, network)
                : node.getElevatedRoadId();
            if (edge != null && elevatedRoadId != null && elevatedRoadId.equals(edge.getRoadId())) {
                return profileY != null ? profileY : networkY;
            }
            return networkY != null ? networkY : profileY;
        }
        if (node.getDegree() >= 3 && networkY != null) {
            return networkY;
        }
        return profileY != null ? profileY : networkY;
    }

    @FunctionalInterface
    public interface ElevatedRoadIdResolver {
        String resolve(RoadNode node, RoadNetwork network);
    }

    private static Integer profileEndpointY(List<SegmentHeightInfo> heightInfos, boolean start) {
        if (heightInfos == null || heightInfos.isEmpty()) {
            return null;
        }
        return start ? heightInfos.getFirst().targetStart : heightInfos.getLast().targetEnd;
    }
}
