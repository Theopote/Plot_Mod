package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

/**
 * 平交路缘圆角有效半径的唯一解析入口（画布预览 / 几何 / 体素共用）。
 * <p>
 * 权威顺序：
 * <ol>
 *   <li>立交 → 0</li>
 *   <li>附近平交 {@link RoadCrossing} 显式 {@code cornerRadius}（含显式 0=关闭）</li>
 *   <li>节点显式 {@code cornerRadius}</li>
 *   <li>否则 0（默认关闭；不继承网络默认半径）</li>
 * </ol>
 * Crossing {@code null} 表示「未设置」，不得覆盖节点已有显式半径。
 */
public final class JunctionCornerRadius {

    private JunctionCornerRadius() {
    }

    public static double resolve(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        if (node != null && (node.isGradeSeparated() || node.isStackGradeSeparated())) {
            return 0.0;
        }
        RoadCrossing crossing = findAtGradeCrossingNear(network, node);
        return resolve(node, crossing, config);
    }

    public static double resolve(RoadNode node, RoadCrossing crossing, RoadSystemConfig config) {
        if (node != null && (node.isGradeSeparated() || node.isStackGradeSeparated())) {
            return 0.0;
        }
        if (crossing != null && crossing.type() == CrossingType.GRADE_SEPARATED) {
            return 0.0;
        }
        if (crossing != null
                && crossing.type() == CrossingType.AT_GRADE
                && crossing.cornerRadius() != null) {
            return Math.max(0.0, crossing.cornerRadius());
        }
        if (node != null && node.getCornerRadius() != null) {
            return Math.max(0.0, node.getCornerRadius());
        }
        return 0.0;
    }

    /**
     * 物化写入节点：Crossing 显式值覆盖；未设置则保留快照上已有节点半径；立交强制 0。
     */
    public static void applyToJunctionNode(RoadNode junction, RoadCrossing crossing) {
        if (junction == null) {
            return;
        }
        if (crossing == null || crossing.type() == CrossingType.GRADE_SEPARATED) {
            junction.setCornerRadius(0.0);
            return;
        }
        if (crossing.cornerRadius() != null) {
            junction.setCornerRadius(crossing.cornerRadius());
        }
        // null：保留 snapshot 拷贝上的节点显式半径（或保持 null→默认关）
    }

    static RoadCrossing findAtGradeCrossingNear(RoadNetwork network, RoadNode node) {
        if (network == null || node == null || node.getPosition() == null) {
            return null;
        }
        RoadCrossing best = null;
        double bestDistance = 0.75;
        for (RoadCrossing crossing : network.getCrossings().values()) {
            if (crossing == null
                    || crossing.type() != CrossingType.AT_GRADE
                    || crossing.position() == null) {
                continue;
            }
            double distance = crossing.position().distance(node.getPosition());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = crossing;
            }
        }
        return best;
    }
}
