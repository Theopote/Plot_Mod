package com.plot.plugin.road.crossing;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelStyle;

/**
 * 立交上下净空：<strong>不低于</strong>下穿道路的隧道设计净高（{@link ResolvedTunnelStyle#clearHeight()}）。
 * 未显式指定时取该下限；显式值更大时保留显式值，更小时抬到下限。
 */
public final class GradeSeparationClearance {
    private GradeSeparationClearance() {
    }

    /**
     * @param explicitClearance 节点/交叉上已保存的净空；null 时用下穿隧道净高下限
     * @param underpassRoad     下穿道路；可为 null（退回配置隧道净高）
     */
    public static double resolve(
            Double explicitClearance,
            Road underpassRoad,
            RoadSystemConfig config) {
        return atLeast(explicitClearance, fromUnderpassRoad(underpassRoad, config));
    }

    /** 下穿道路隧道净高下限；无道路时用配置默认隧道净高。 */
    public static double fromUnderpassRoad(Road underpassRoad, RoadSystemConfig config) {
        ResolvedTunnelStyle style = TunnelStyle.resolve(underpassRoad, config);
        return minimumSurfaceSeparation(style.clearHeight());
    }

    /**
     * 路口立交：下限为非上跨道路中最大隧道净高（多肢下穿时取最严）。
     */
    public static double forNode(
            Double explicitClearance,
            RoadNode node,
            RoadNetwork network,
            String elevatedRoadId,
            RoadSystemConfig config) {
        return atLeast(explicitClearance, minimumForNode(node, network, elevatedRoadId, config));
    }

    /** 路口立交净空下限（下穿侧隧道净高）。 */
    public static double minimumForNode(
            RoadNode node,
            RoadNetwork network,
            String elevatedRoadId,
            RoadSystemConfig config) {
        if (network == null || node == null) {
            return fromUnderpassRoad(null, config);
        }
        int maxClear = 0;
        boolean found = false;
        for (String roadId : network.getDistinctRoadIdsAtNode(node.getId())) {
            if (elevatedRoadId != null && elevatedRoadId.equals(roadId)) {
                continue;
            }
            Road road = network.getRoad(roadId);
            int clear = TunnelStyle.resolve(road, config).clearHeight();
            maxClear = Math.max(maxClear, clear);
            found = true;
        }
        if (!found) {
            // 自交立交：上下为同一条路
            if (elevatedRoadId != null) {
                return fromUnderpassRoad(network.getRoad(elevatedRoadId), config);
            }
            return fromUnderpassRoad(null, config);
        }
        return minimumSurfaceSeparation(maxClear);
    }

    /** Constraints operate on pavement elevations; the upper support layer consumes headroom. */
    private static double minimumSurfaceSeparation(int tunnelClearHeight) {
        return RoadParameterLimits.clampCrossingClearance(tunnelClearHeight
            + com.plot.plugin.road.pipeline.corridor.JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH);
    }

    /** 两路交叉：不低于下穿侧隧道净高。 */
    public static double forTwoRoadCrossing(
            Double explicitClearance,
            RoadNetwork network,
            String elevatedRoadId,
            String roadAId,
            String roadBId,
            RoadSystemConfig config) {
        String underpassId = underpassRoadId(elevatedRoadId, roadAId, roadBId);
        Road underpass = network != null && underpassId != null ? network.getRoad(underpassId) : null;
        return resolve(explicitClearance, underpass, config);
    }

    public static String underpassRoadId(String elevatedRoadId, String roadAId, String roadBId) {
        if (elevatedRoadId != null && elevatedRoadId.equals(roadAId)) {
            return roadBId;
        }
        if (elevatedRoadId != null && elevatedRoadId.equals(roadBId)) {
            return roadAId;
        }
        return roadBId != null ? roadBId : roadAId;
    }

    /** 显式净空与下限取较大者；显式为 null 时直接用下限。 */
    public static double atLeast(Double explicitClearance, double minimum) {
        double floor = RoadParameterLimits.clampCrossingClearance(minimum);
        if (explicitClearance == null) {
            return floor;
        }
        return RoadParameterLimits.clampCrossingClearance(Math.max(explicitClearance, floor));
    }
}
