package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.List;
import java.util.function.Function;

/**
 * 路口几何生成用的中心线解析：统一走 {@link RoadPlanGeometry}，禁止生成路径直接读
 * {@link RoadEdge#getCenterlinePoints()}。
 */
public final class RoadJunctionCenterlineResolver {
    private RoadJunctionCenterlineResolver() {
    }

    public static Function<RoadEdge, List<Vec2d>> forNetwork(RoadNetwork network) {
        return edge -> RoadPlanGeometry.resolveEdgeCenterline(network, edge);
    }

    public static Function<RoadEdge, List<Vec2d>> forNetwork(RoadNetwork network, double sampleSpacingMeters) {
        return edge -> RoadPlanGeometry.resolveEdgeCenterline(network, edge, sampleSpacingMeters);
    }
}
