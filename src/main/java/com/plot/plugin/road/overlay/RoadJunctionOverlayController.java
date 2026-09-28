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

    /**
     * 画布点击命中测试：返回距点击位置最近的交叉点节点 id。
     */
    public static String hitTest(
            List<RoadJunctionOverlayEntry> entries,
            double worldX,
            double worldY,
            double worldRadius) {
        if (entries == null || entries.isEmpty() || worldRadius <= 0.0) {
            return null;
        }
        String closestNodeId = null;
        double closestDistance = worldRadius;
        for (RoadJunctionOverlayEntry entry : entries) {
            if (entry == null || entry.position() == null) {
                continue;
            }
            double distance = Math.hypot(
                worldX - entry.position().x,
                worldY - entry.position().y);
            if (distance <= closestDistance) {
                closestDistance = distance;
                closestNodeId = entry.nodeId();
            }
        }
        return closestNodeId;
    }
}
