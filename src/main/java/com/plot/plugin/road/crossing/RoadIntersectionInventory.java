package com.plot.plugin.road.crossing;

import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 可编辑交点清单：交叉注册表 + 自交 + 未被注册表覆盖的遗留拓扑路口。
 * <p>
 * 与路径 Tab「交叉关系」列表口径一致；摘要统计应使用 {@link #count(RoadNetwork)}，
 * 勿再用仅统计度≥3 拓扑节点的 {@link RoadNetwork#getJunctionCount()}。
 */
public final class RoadIntersectionInventory {
    private static final double COVER_TOLERANCE = 0.5;

    private RoadIntersectionInventory() {
    }

    /** 可编辑交点总数（注册表交叉 + 自交 + 遗留拓扑路口）。 */
    public static int count(RoadNetwork network) {
        if (network == null) {
            return 0;
        }
        return network.getCrossings().size()
            + network.getStackCrossings().size()
            + listLegacyJunctionNodes(network).size();
    }

    /**
     * 遗留拓扑路口：度≥2 且表现为路口/平交/立交，且位置未被交叉注册表覆盖。
     */
    public static List<RoadNode> listLegacyJunctionNodes(RoadNetwork network) {
        if (network == null) {
            return List.of();
        }
        List<RoadCrossing> crossings = List.copyOf(network.getCrossings().values());
        List<RoadStackCrossing> stackCrossings = List.copyOf(network.getStackCrossings().values());
        List<RoadNode> junctions = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getDegree() < 2) {
                continue;
            }
            if (isCoveredByRegistryCrossing(network, node, crossings)
                    || isCoveredByStackCrossing(network, node, stackCrossings)) {
                continue;
            }
            if (node.isJunction()
                    || RoadGraphQueries.isSimpleCrossing(node, network)
                    || node.isGradeSeparated()) {
                junctions.add(node);
            }
        }
        junctions.sort(Comparator.comparingInt(RoadNode::getDegree).reversed());
        return junctions;
    }

    private static boolean isCoveredByStackCrossing(
            RoadNetwork network,
            RoadNode node,
            List<RoadStackCrossing> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return false;
        }
        String boundId = node.getStackCrossingId();
        if (boundId != null && !boundId.isBlank()) {
            for (RoadStackCrossing stack : stacks) {
                if (boundId.equals(stack.id())) {
                    return true;
                }
            }
        }
        Set<String> nodeRoadIds = incidentRoadIds(network, node);
        if (node.getPosition() == null) {
            return false;
        }
        for (RoadStackCrossing stack : stacks) {
            if (stack.position() == null) {
                continue;
            }
            if (!RoadGeometryUtils.pointsNear(node.getPosition(), stack.position(), COVER_TOLERANCE)) {
                continue;
            }
            if (stack.roadId() != null && nodeRoadIds.contains(stack.roadId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCoveredByRegistryCrossing(
            RoadNetwork network,
            RoadNode node,
            List<RoadCrossing> crossings) {
        if (node.getPosition() == null || crossings == null || crossings.isEmpty()) {
            return false;
        }
        Set<String> nodeRoadIds = incidentRoadIds(network, node);
        for (RoadCrossing crossing : crossings) {
            if (crossing.position() == null) {
                continue;
            }
            // Require the full participating road pair — a single shared road is not enough
            // to hide an independent co-located junction (e.g. A-B node vs A-C registry).
            if (!roadsFullyCover(nodeRoadIds, crossing)) {
                continue;
            }
            if (RoadGeometryUtils.pointsNear(node.getPosition(), crossing.position(), COVER_TOLERANCE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean roadsFullyCover(Set<String> nodeRoadIds, RoadCrossing crossing) {
        if (nodeRoadIds == null || nodeRoadIds.isEmpty() || crossing == null) {
            return false;
        }
        return nodeRoadIds.contains(crossing.roadAId()) && nodeRoadIds.contains(crossing.roadBId());
    }

    private static Set<String> incidentRoadIds(RoadNetwork network, RoadNode node) {
        Set<String> roadIds = new HashSet<>();
        if (network == null || node == null) {
            return roadIds;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && edge.getRoadId() != null) {
                roadIds.add(edge.getRoadId());
            }
        }
        return roadIds;
    }
}
