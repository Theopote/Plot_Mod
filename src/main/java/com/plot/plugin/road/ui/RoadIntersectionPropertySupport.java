package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

/** 路口/Crossing 选中态解析（属性侧栏与编辑 Tab 共用）。 */
public final class RoadIntersectionPropertySupport {

    private static final double NODE_MATCH_TOLERANCE = 0.5;

    private RoadIntersectionPropertySupport() {
    }

    public static boolean hasIntersectionSelection(RoadNetworkManager manager) {
        if (manager == null) {
            return false;
        }
        if (manager.getSelectedCrossing() != null || manager.getSelectedStackCrossing() != null) {
            return true;
        }
        RoadNode node = manager.getSelectedNode();
        return node != null && node.getDegree() >= 2;
    }

    static boolean isGradeSeparatedSelection(RoadNetworkManager manager) {
        RoadCrossing crossing = manager.getSelectedCrossing();
        if (crossing != null) {
            return crossing.type() == com.plot.plugin.road.crossing.CrossingType.GRADE_SEPARATED;
        }
        RoadStackCrossing stack = manager.getSelectedStackCrossing();
        if (stack != null) {
            return stack.isGradeSeparated();
        }
        RoadNode node = manager.getSelectedNode();
        return node != null && node.isGradeSeparated();
    }

    /**
     * 解析可用于圆角/标线的拓扑节点：优先选中节点，否则在 Crossing 位置附近匹配。
     */
    static RoadNode resolveJunctionNode(RoadNetwork network, RoadNetworkManager manager) {
        if (network == null || manager == null) {
            return null;
        }
        RoadNode selected = manager.getSelectedNode();
        if (selected != null && selected.getDegree() >= 2) {
            return selected;
        }
        Vec2d position = resolveSelectionPosition(network, manager);
        if (position == null) {
            return null;
        }
        return findNodeNear(network, position);
    }

    private static Vec2d resolveSelectionPosition(RoadNetwork network, RoadNetworkManager manager) {
        RoadCrossing crossing = manager.getSelectedCrossing();
        if (crossing != null && crossing.position() != null) {
            return crossing.position();
        }
        RoadStackCrossing stack = manager.getSelectedStackCrossing();
        if (stack != null && stack.position() != null) {
            return stack.position();
        }
        RoadNode node = manager.getSelectedNode();
        return node != null ? node.getPosition() : null;
    }

    private static RoadNode findNodeNear(RoadNetwork network, Vec2d position) {
        RoadNode best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getPosition() == null || node.getDegree() < 2) {
                continue;
            }
            if (!RoadGeometryUtils.pointsNear(node.getPosition(), position, NODE_MATCH_TOLERANCE)) {
                continue;
            }
            double distance = node.getPosition().distance(position);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = node;
            }
        }
        return best;
    }
}
