package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * 将 {@link RoadHorizontalAlignment} 原点与链起点（首段入口节点）对齐。
 * <p>
 * 仅在实例中心线桩号 0 与链入口节点重合时更新原点，避免共享路口节点未移动时平移设计线形。
 */
public final class HorizontalAlignmentChainOriginAligner {

    private static final double ANCHOR_TOLERANCE_METERS = 1e-3;

    private HorizontalAlignmentChainOriginAligner() {
    }

    public static boolean alignToChainStart(RoadNetwork network, Road road) {
        RoadHorizontalAlignment alignment = road != null ? road.getHorizontalAlignment() : null;
        if (network == null || road == null || alignment == null || alignment.isEmpty()) {
            return false;
        }

        Optional<Vec2d> chainOrigin = RoadStationing.chainOrigin(network, road);
        Optional<Vec2d> centerlineStart = RoadPlanGeometry.instancePointAtStation(network, road, 0.0);
        if (chainOrigin.isEmpty() || centerlineStart.isEmpty()) {
            return false;
        }
        if (centerlineStart.get().distance(chainOrigin.get()) > ANCHOR_TOLERANCE_METERS) {
            return false;
        }

        boolean changed = false;
        Vec2d targetOrigin = chainOrigin.get();
        if (alignment.getOrigin().distance(targetOrigin) > ANCHOR_TOLERANCE_METERS) {
            alignment.setOrigin(targetOrigin.copy());
            changed = true;
        }

        OptionalDouble bearing = bearingAtChainStart(network, road);
        if (bearing.isPresent()
                && !anglesEqual(alignment.getStartBearingRadians(), bearing.getAsDouble())) {
            alignment.setStartBearingRadians(bearing.getAsDouble());
            changed = true;
        }
        return changed;
    }

    private static OptionalDouble bearingAtChainStart(RoadNetwork network, Road road) {
        List<OrientedRoadSegment> segments = RoadStationing.orientedSegments(network, road);
        if (segments.isEmpty()) {
            return OptionalDouble.empty();
        }
        return bearingAtChainStart(network, segments.getFirst());
    }

    private static OptionalDouble bearingAtChainStart(RoadNetwork network, OrientedRoadSegment segment) {
        RoadEdge edge = network.getEdge(segment.edgeId());
        if (edge == null) {
            return OptionalDouble.empty();
        }
        return bearingAtChainStart(edge.getCenterlinePoints(), segment.forward());
    }

    static OptionalDouble bearingAtChainStart(List<Vec2d> centerlinePoints, boolean forward) {
        if (centerlinePoints == null || centerlinePoints.size() < 2) {
            return OptionalDouble.empty();
        }
        Vec2d from;
        Vec2d to;
        if (forward) {
            from = centerlinePoints.getFirst();
            to = centerlinePoints.get(1);
        } else {
            from = centerlinePoints.getLast();
            to = centerlinePoints.get(centerlinePoints.size() - 2);
        }
        Vec2d direction = to.subtract(from);
        if (direction.lengthSquared() < 1e-12) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.atan2(direction.y, direction.x));
    }

    private static boolean anglesEqual(double left, double right) {
        double delta = Math.abs(Math.atan2(Math.sin(left - right), Math.cos(left - right)));
        return delta <= 1e-9;
    }
}
