package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

/**
 * 路口/Crossing 选中态解析（属性侧栏与编辑 Tab 共用）。
 * 平交圆角以 {@link RoadCrossing} 为权威；真实节点仅在存在时同步。
 */
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

    /** 是否可展示/编辑平交路缘圆角（立交与栈式立交禁用）。 */
    public static boolean canEditFillets(RoadNetworkManager manager) {
        if (manager == null || isGradeSeparatedSelection(manager)) {
            return false;
        }
        RoadCrossing crossing = manager.getSelectedCrossing();
        if (crossing != null) {
            return crossing.type() == CrossingType.AT_GRADE;
        }
        if (manager.getSelectedStackCrossing() != null) {
            return false;
        }
        RoadNode node = manager.getSelectedNode();
        return node != null && node.getDegree() >= 2 && !node.isGradeSeparated();
    }

    static boolean isGradeSeparatedSelection(RoadNetworkManager manager) {
        RoadCrossing crossing = manager.getSelectedCrossing();
        if (crossing != null) {
            return crossing.type() == CrossingType.GRADE_SEPARATED;
        }
        RoadStackCrossing stack = manager.getSelectedStackCrossing();
        if (stack != null) {
            return stack.isGradeSeparated();
        }
        RoadNode node = manager.getSelectedNode();
        return node != null && node.isGradeSeparated();
    }

    /**
     * 解析可用于标线/广场等依赖拓扑的节点：优先选中节点，否则在 Crossing 位置附近匹配。
     * 圆角编辑不要依赖此方法——无共享节点的普通平交也应可编辑。
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

    /** 当前选中对象的有效圆角（Crossing 优先于 Node）。 */
    public static double resolveEffectiveCornerRadius(
            RoadNetworkManager manager,
            RoadSystemConfig config) {
        double networkDefault = config != null ? config.getDefaultCornerRadius() : 0.0;
        if (manager == null) {
            return networkDefault;
        }
        if (isGradeSeparatedSelection(manager)) {
            return 0.0;
        }
        RoadCrossing crossing = manager.getSelectedCrossing();
        if (crossing != null && crossing.type() == CrossingType.AT_GRADE) {
            return crossing.effectiveCornerRadius(networkDefault);
        }
        RoadNode node = resolveJunctionNode(manager.getNetwork(), manager);
        if (node != null) {
            return node.getEffectiveCornerRadius(networkDefault);
        }
        return networkDefault;
    }

    /** 原始存储值：null = 跟随网络默认。Crossing 优先。 */
    public static Double resolveStoredCornerRadius(RoadNetworkManager manager) {
        if (manager == null || isGradeSeparatedSelection(manager)) {
            return 0.0;
        }
        RoadCrossing crossing = manager.getSelectedCrossing();
        if (crossing != null && crossing.type() == CrossingType.AT_GRADE) {
            return crossing.cornerRadius();
        }
        RoadNode node = resolveJunctionNode(manager.getNetwork(), manager);
        return node != null ? node.getCornerRadius() : null;
    }

    /**
     * 写入圆角：AT_GRADE Crossing 写注册表；若附近/选中有节点则同步节点。
     * <p>调用方负责 {@code pushHistory}/{@code isItemActivated} 撤销帧；此处不二次入栈。
     */
    public static void applyCornerRadius(RoadNetworkManager manager, Double cornerRadius) {
        if (manager == null) {
            return;
        }
        RoadNetwork network = manager.getNetwork();
        RoadCrossing crossing = manager.getSelectedCrossing();
        if (crossing != null && crossing.type() == CrossingType.AT_GRADE) {
            network.setCrossingCornerRadius(crossing.id(), cornerRadius);
        }
        RoadNode node = resolveJunctionNode(network, manager);
        if (node != null && !node.isGradeSeparated()) {
            node.setCornerRadius(cornerRadius);
        }
    }

    public static Vec2d resolveSelectionPosition(RoadNetwork network, RoadNetworkManager manager) {
        if (manager == null) {
            return null;
        }
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
