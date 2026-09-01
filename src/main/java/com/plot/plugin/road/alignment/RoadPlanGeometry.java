package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.SegmentStation;

import java.util.List;
import java.util.Optional;

/**
 * 道路平面几何统一查询：有设计平面线形时以 {@link RoadHorizontalAlignment} 为权威，
 * 否则回退到 {@link RoadEdge#getCenterlinePoints()}。
 * <p>
 * 工程桩号域长度见 {@link #canonicalLength}；设计线形长见 {@link #designLength}；
 * 实例折线链长见 {@link #instanceLength}。
 */
public final class RoadPlanGeometry {

    private static final double STATION_EPSILON = 1e-6;

    private RoadPlanGeometry() {
    }

    public static boolean hasDesignAlignment(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return false;
        }
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        return alignment != null
            && !alignment.isEmpty()
            && RoadStationing.isStationable(network, road);
    }

    public static boolean usesDesignAlignment(RoadNetwork network, RoadEdge edge) {
        if (edge == null || edge.getRoadId() == null) {
            return false;
        }
        Road road = network != null ? network.getRoad(edge.getRoadId()) : null;
        return hasDesignAlignment(network, road);
    }

    public static RoadGeometryAuthority authority(RoadNetwork network, RoadEdge edge) {
        return usesDesignAlignment(network, edge)
            ? RoadGeometryAuthority.DESIGN_HORIZONTAL_ALIGNMENT
            : RoadGeometryAuthority.INSTANCE_CENTERLINE;
    }

    /**
     * Canonical 道路链长（工程桩号域权威上界）。
     * <p>
     * 有有效 HA 时取 {@link #designLength}，否则取 {@link #instanceLength}。
     */
    public static double canonicalLength(RoadNetwork network, Road road) {
        if (hasDesignAlignment(network, road)) {
            return designLength(network, road);
        }
        return instanceLength(network, road);
    }

    /**
     * 设计平面线形总长（{@link RoadHorizontalAlignment}）；无 HA 时为 0。
     */
    public static double designLength(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return 0.0;
        }
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        if (alignment == null || alignment.isEmpty()) {
            return 0.0;
        }
        return HorizontalAlignmentGeometry.totalLength(alignment);
    }

    /**
     * 实例折线链长（{@link RoadEdge} 派生几何累计弧长）。
     */
    public static double instanceLength(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return 0.0;
        }
        double total = 0.0;
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            total += segment.length();
        }
        return total;
    }

    /** @deprecated 使用 {@link #designLength} */
    @Deprecated
    public static double planLength(RoadNetwork network, Road road) {
        return designLength(network, road);
    }

    /** @deprecated 使用 {@link #instanceLength} */
    @Deprecated
    public static double instanceChainLength(RoadNetwork network, Road road) {
        return instanceLength(network, road);
    }

    public static Optional<Vec2d> pointAtStation(RoadNetwork network, Road road, double chainageMeters) {
        if (hasDesignAlignment(network, road)) {
            return HorizontalAlignmentGeometry.poseAt(road.getHorizontalAlignment(), chainageMeters)
                .map(pose -> new Vec2d(pose.x(), pose.y()));
        }
        return instancePointAtStation(network, road, chainageMeters);
    }

    public static Optional<AlignmentPose> poseAtStation(RoadNetwork network, Road road, double chainageMeters) {
        if (hasDesignAlignment(network, road)) {
            return HorizontalAlignmentGeometry.poseAt(road.getHorizontalAlignment(), chainageMeters);
        }
        return instancePointAtStation(network, road, chainageMeters)
            .flatMap(point -> instanceBearingAtStation(network, road, chainageMeters)
                .map(bearing -> new AlignmentPose(point.x, point.y, bearing, 0.0)));
    }

    public static Optional<Double> bearingAtStation(RoadNetwork network, Road road, double chainageMeters) {
        if (hasDesignAlignment(network, road)) {
            return HorizontalAlignmentGeometry.poseAt(road.getHorizontalAlignment(), chainageMeters)
                .map(AlignmentPose::bearingRadians);
        }
        return instanceBearingAtStation(network, road, chainageMeters);
    }

    public static Optional<Vec2d> instancePointAtStation(
            RoadNetwork network,
            Road road,
            double chainageMeters) {
        return RoadStationing.edgeLocalDistanceAtRoadStation(network, road, chainageMeters)
            .flatMap(segment -> instancePointAtEdgeLocal(
                network,
                road,
                segment.segmentId(),
                segment.localDistance()));
    }

    public static Optional<Vec2d> pointAtEdgeLocal(
            RoadNetwork network,
            Road road,
            String edgeId,
            double geometryLocalDistance) {
        if (usesDesignAlignment(network, network != null ? network.getEdge(edgeId) : null)) {
            return RoadStationing.stationAt(network, road, edgeId, geometryLocalDistance)
                .flatMap(station -> pointAtStation(network, road, station.chainageMeters()));
        }
        return instancePointAtEdgeLocal(network, road, edgeId, geometryLocalDistance);
    }

    public static Optional<Vec2d> instancePointAtEdgeLocal(
            RoadNetwork network,
            Road road,
            String edgeId,
            double geometryLocalDistance) {
        if (network == null || road == null || edgeId == null || edgeId.isBlank()) {
            return Optional.empty();
        }
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null) {
            return Optional.empty();
        }
        Vec2d point = RoadGeometryUtils.pointAtDistance(edge.getCenterlinePoints(), geometryLocalDistance);
        return point != null ? Optional.of(point) : Optional.empty();
    }

    public static List<Vec2d> resolveEdgeCenterline(RoadNetwork network, RoadEdge edge) {
        return resolveEdgeCenterline(
            network,
            edge,
            HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
    }

    public static List<Vec2d> resolveEdgeCenterline(
            RoadNetwork network,
            RoadEdge edge,
            double sampleSpacingMeters) {
        if (edge == null) {
            return List.of();
        }
        Optional<OrientedRoadSegment> oriented = resolveOrientedSegment(network, edge);
        if (oriented.isEmpty()) {
            return edge.getCenterlinePoints();
        }
        Road road = network.getRoad(edge.getRoadId());
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        List<Vec2d> sampled = HorizontalAlignmentCenterlineMaterializer.sampleGeometryPoints(
            alignment,
            oriented.get(),
            sampleSpacingMeters);
        if (sampled.size() < 2) {
            return edge.getCenterlinePoints();
        }
        return sampled;
    }

    private static Optional<Double> instanceBearingAtStation(
            RoadNetwork network,
            Road road,
            double chainageMeters) {
        Optional<SegmentStation> segment = RoadStationing.edgeLocalDistanceAtRoadStation(network, road, chainageMeters);
        if (segment.isEmpty()) {
            return Optional.empty();
        }
        RoadEdge edge = network.getEdge(segment.get().segmentId());
        if (edge == null) {
            return Optional.empty();
        }
        return instanceBearingAtEdgeLocal(
            network,
            road,
            edge,
            segment.get().localDistance());
    }

    private static Optional<Double> instanceBearingAtEdgeLocal(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            double geometryLocalDistance) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points == null || points.size() < 2) {
            return Optional.empty();
        }
        Optional<OrientedRoadSegment> oriented = RoadStationing.orientedSegment(network, road, edge.getId());
        if (oriented.isEmpty()) {
            return Optional.empty();
        }

        double clamped = Math.max(0.0, Math.min(geometryLocalDistance, edge.getLength()));
        Vec2d from;
        Vec2d to;
        if (oriented.get().forward()) {
            Vec2d point = RoadGeometryUtils.pointAtDistance(points, clamped);
            if (point == null) {
                return Optional.empty();
            }
            double ahead = Math.min(clamped + STATION_EPSILON, edge.getLength());
            Vec2d aheadPoint = RoadGeometryUtils.pointAtDistance(points, ahead);
            if (aheadPoint == null || point.distance(aheadPoint) < STATION_EPSILON) {
                if (clamped <= STATION_EPSILON) {
                    from = points.getFirst();
                    to = points.get(1);
                } else {
                    from = points.get(points.size() - 2);
                    to = points.getLast();
                }
            } else {
                from = point;
                to = aheadPoint;
            }
        } else {
            // geometryLocalDistance 是链局部距离；point→behindPoint 已沿 Road chain 方向。
            double geometryFromEnd = edge.getLength() - clamped;
            Vec2d point = RoadGeometryUtils.pointAtDistance(points, geometryFromEnd);
            if (point == null) {
                return Optional.empty();
            }
            double behind = Math.max(geometryFromEnd - STATION_EPSILON, 0.0);
            Vec2d behindPoint = RoadGeometryUtils.pointAtDistance(points, behind);
            if (behindPoint == null || point.distance(behindPoint) < STATION_EPSILON) {
                from = points.getLast();
                to = points.get(points.size() - 2);
            } else {
                from = point;
                to = behindPoint;
            }
        }
        Vec2d direction = to.subtract(from);
        if (direction.lengthSquared() < STATION_EPSILON * STATION_EPSILON) {
            return Optional.empty();
        }
        return Optional.of(Math.atan2(direction.y, direction.x));
    }

    private static Optional<OrientedRoadSegment> resolveOrientedSegment(
            RoadNetwork network,
            RoadEdge edge) {
        if (network == null || edge == null || edge.getRoadId() == null) {
            return Optional.empty();
        }
        if (!usesDesignAlignment(network, edge)) {
            return Optional.empty();
        }
        return RoadStationing.orientedSegment(network, network.getRoad(edge.getRoadId()), edge.getId());
    }
}
