package com.plot.plugin.road.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 分析单条逻辑道路内分段拓扑形态（基于 {@link RoadTopologyInvariantValidator}）。
 * <p>
 * 供工程检查与 UI 摘要使用；不修改网络、不阻断操作。
 *
 * @deprecated 新代码请直接使用 {@link RoadTopologyInvariantValidator} 与 {@link RoadTopologyViolationKind}。
 */
@Deprecated
public final class RoadSegmentTopologyAnalyzer {

    private RoadSegmentTopologyAnalyzer() {
    }

    public static RoadSegmentTopologyKind classify(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return RoadSegmentTopologyKind.SIMPLE_CHAIN;
        }
        for (RoadTopologyViolation violation : RoadTopologyInvariantValidator.validateRoad(network, road)) {
            return switch (violation.kind()) {
                case ROAD_DISCONNECTED -> RoadSegmentTopologyKind.DISCONNECTED;
                case ROAD_BRANCHING -> RoadSegmentTopologyKind.FORK;
                case ROAD_CYCLE -> RoadSegmentTopologyKind.LOOP;
                case ROAD_ORDER_MISMATCH -> RoadSegmentTopologyKind.SIMPLE_CHAIN;
            };
        }
        return RoadSegmentTopologyKind.SIMPLE_CHAIN;
    }

    public static int countNonSimpleChainRoads(RoadNetwork network) {
        return RoadTopologyInvariantValidator.countRoadsWithShapeViolations(network);
    }

    public static List<String> nonSimpleChainRoadIds(RoadNetwork network) {
        if (network == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            if (classify(network, road) != RoadSegmentTopologyKind.SIMPLE_CHAIN) {
                result.add(road.getId());
            }
        }
        return List.copyOf(result);
    }
}
