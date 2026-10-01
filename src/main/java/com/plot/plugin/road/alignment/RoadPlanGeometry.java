package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadLoopSeamService;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.SegmentStation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

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
        Road road = network != null ? network.getRoadForEdge(edge) : null;
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

    public static Optional<Vec2d> pointAtStation(RoadNetwork network, Road road, double chainageMeters) {
        if (hasDesignAlignment(network, road)) {
            return HorizontalAlignmentGeometry.poseAt(
                    road.getHorizontalAlignment(),
                    haNativeStation(network, road, chainageMeters))
                .map(pose -> new Vec2d(pose.x(), pose.y()));
        }
        return instancePointAtStation(network, road, chainageMeters);
    }

    public static Optional<AlignmentPose> poseAtStation(RoadNetwork network, Road road, double chainageMeters) {
        if (hasDesignAlignment(network, road)) {
            return HorizontalAlignmentGeometry.poseAt(
                road.getHorizontalAlignment(),
                haNativeStation(network, road, chainageMeters));
        }
        return instancePointAtStation(network, road, chainageMeters)
            .flatMap(point -> instanceBearingAtStation(network, road, chainageMeters)
                .map(bearing -> new AlignmentPose(point.x, point.y, bearing, 0.0)));
    }

    public static Optional<Double> bearingAtStation(RoadNetwork network, Road road, double chainageMeters) {
        if (hasDesignAlignment(network, road)) {
            return HorizontalAlignmentGeometry.poseAt(
                    road.getHorizontalAlignment(),
                    haNativeStation(network, road, chainageMeters))
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
        List<PlanCenterlineSample> samples = resolveEdgeCenterlineSamples(
            network, edge, sampleSpacingMeters);
        if (samples.size() < 2) {
            return edge.getCenterlinePoints();
        }
        return samples.stream().map(PlanCenterlineSample::position).toList();
    }

    /**
     * 整条 Road 的 plan 中心线采样（canonical 0 → L）；HA 与实例折线统一入口。
     */
    public static List<PlanCenterlineSample> resolveRoadCenterlineSamples(RoadNetwork network, Road road) {
        return resolveRoadCenterlineSamples(
            network,
            road,
            HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
    }

    public static List<PlanCenterlineSample> resolveRoadCenterlineSamples(
            RoadNetwork network,
            Road road,
            double sampleSpacingMeters) {
        if (network == null || road == null || network.getRoad(road.getId()) == null) {
            return List.of();
        }
        double spacing = sampleSpacingMeters > STATION_EPSILON
            ? sampleSpacingMeters
            : HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS;
        double total = canonicalLength(network, road);
        if (total <= STATION_EPSILON) {
            return List.of();
        }
        List<PlanCenterlineSample> samples = new ArrayList<>();
        for (double chainage = 0.0; chainage <= total + STATION_EPSILON; chainage += spacing) {
            double clamped = Math.min(chainage, total);
            appendRoadSampleIfDistinct(network, road, samples, clamped);
        }
        appendRoadSampleIfDistinct(network, road, samples, total);
        return List.copyOf(samples);
    }

    /**
     * 带设计 canonical 桩号的 plan 中心线采样；用于坐标 → 桩号反查。
     * <p>
     * 从 {@link #resolveRoadCenterlineSamples} 按 Edge 所属 slice 过滤，覆盖 LOOP interior seam 双 slice。
     */
    public static List<PlanCenterlineSample> resolveEdgeCenterlineSamples(RoadNetwork network, RoadEdge edge) {
        return resolveEdgeCenterlineSamples(
            network,
            edge,
            HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
    }

    public static List<PlanCenterlineSample> resolveEdgeCenterlineSamples(
            RoadNetwork network,
            RoadEdge edge,
            double sampleSpacingMeters) {
        if (edge == null || network == null || edge.getRoadId() == null) {
            return List.of();
        }
        Road road = network.getRoadForEdge(edge);
        if (road == null) {
            return List.of();
        }
        List<OrientedRoadSegment> slices = RoadStationing.orientedSegmentsForEdge(network, road, edge.getId());
        if (slices.isEmpty()) {
            return List.of();
        }
        return filterSamplesForSlices(
            network,
            road,
            resolveRoadCenterlineSamples(network, road, sampleSpacingMeters),
            slices);
    }

    /**
     * 在单条边的 plan 采样折线上反查 canonical 桩号。
     */
    public static OptionalDouble chainageAtPositionOnPlanSamples(
            Vec2d position,
            List<PlanCenterlineSample> samples) {
        if (position == null || samples == null || samples.size() < 2) {
            return OptionalDouble.empty();
        }
        double bestDistance = Double.MAX_VALUE;
        Double bestChainage = null;
        for (int i = 0; i < samples.size() - 1; i++) {
            PlanCenterlineSample start = samples.get(i);
            PlanCenterlineSample end = samples.get(i + 1);
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(
                start.position(), end.position(), position);
            double distance = projected.distance(position);
            if (distance > com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE || distance >= bestDistance) {
                continue;
            }
            double span = start.position().distance(end.position());
            double t = span <= STATION_EPSILON
                ? 0.0
                : start.position().distance(projected) / span;
            bestDistance = distance;
            bestChainage = start.canonicalStation() + t * (end.canonicalStation() - start.canonicalStation());
        }
        return bestChainage != null ? OptionalDouble.of(bestChainage) : OptionalDouble.empty();
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
        return RoadStationing.orientedSegment(network, network.getRoadForEdge(edge), edge.getId());
    }

    /**
     * LOOP canonical 桩号 → HA native 桩号；LINEAR 或无 seam 时恒等。
     */
    static double haNativeStation(RoadNetwork network, Road road, double canonicalStation) {
        if (road == null || !Double.isFinite(canonicalStation)) {
            return 0.0;
        }
        if (road.getTopologyMode() != RoadTopologyMode.LOOP || road.getLoopSeam() == null) {
            return canonicalStation;
        }
        double designLength = designLength(network, road);
        if (designLength <= STATION_EPSILON) {
            return canonicalStation;
        }
        double offset = seamHaNativeOffset(network, road);
        double nativeStation = canonicalStation + offset;
        if (nativeStation >= designLength - STATION_EPSILON) {
            nativeStation -= designLength;
        }
        return Math.max(0.0, Math.min(nativeStation, designLength));
    }

    private static double seamHaNativeOffset(RoadNetwork network, Road road) {
        if (network == null || road == null
                || road.getTopologyMode() != RoadTopologyMode.LOOP
                || road.getLoopSeam() == null) {
            return 0.0;
        }
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        if (alignment == null || alignment.isEmpty()) {
            return 0.0;
        }
        Vec2d seamPosition = RoadLoopSeamService.overlayPosition(network, road);
        if (seamPosition == null) {
            return 0.0;
        }
        List<PlanCenterlineSample> nativeSamples = sampleHaNativeUnrotated(
            alignment,
            HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
        return chainageAtPositionOnPlanSamples(seamPosition, nativeSamples).orElse(0.0);
    }

    private static List<PlanCenterlineSample> sampleHaNativeUnrotated(
            RoadHorizontalAlignment alignment,
            double sampleSpacingMeters) {
        double total = HorizontalAlignmentGeometry.totalLength(alignment);
        if (total <= STATION_EPSILON) {
            return List.of();
        }
        double spacing = sampleSpacingMeters > STATION_EPSILON
            ? sampleSpacingMeters
            : HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS;
        List<PlanCenterlineSample> samples = new ArrayList<>();
        for (double chainage = 0.0; chainage <= total + STATION_EPSILON; chainage += spacing) {
            double clamped = Math.min(chainage, total);
            HorizontalAlignmentGeometry.poseAt(alignment, clamped).ifPresent(pose -> {
                Vec2d point = new Vec2d(pose.x(), pose.y());
                if (samples.isEmpty()
                        || samples.getLast().position().distance(point) > STATION_EPSILON) {
                    samples.add(new PlanCenterlineSample(point, clamped));
                }
            });
        }
        HorizontalAlignmentGeometry.poseAt(alignment, total).ifPresent(pose -> {
            Vec2d point = new Vec2d(pose.x(), pose.y());
            if (samples.isEmpty()
                    || samples.getLast().position().distance(point) > STATION_EPSILON) {
                samples.add(new PlanCenterlineSample(point, total));
            }
        });
        return List.copyOf(samples);
    }

    private static void appendRoadSampleIfDistinct(
            RoadNetwork network,
            Road road,
            List<PlanCenterlineSample> samples,
            double canonicalStation) {
        pointAtStation(network, road, canonicalStation).ifPresent(point -> {
            if (samples.isEmpty()
                    || samples.getLast().position().distance(point) > STATION_EPSILON
                    || Math.abs(samples.getLast().canonicalStation() - canonicalStation) > STATION_EPSILON) {
                samples.add(new PlanCenterlineSample(point, canonicalStation));
            }
        });
    }

    private static List<PlanCenterlineSample> filterSamplesForSlices(
            RoadNetwork network,
            Road road,
            List<PlanCenterlineSample> roadSamples,
            List<OrientedRoadSegment> slices) {
        if (roadSamples.isEmpty() || slices.isEmpty()) {
            return List.of();
        }
        List<PlanCenterlineSample> filtered = new ArrayList<>();
        for (OrientedRoadSegment slice : slices) {
            double start = RoadStationing.toCanonicalChainage(network, road, slice.startStation());
            double end = RoadStationing.toCanonicalChainage(network, road, slice.endStation());
            List<PlanCenterlineSample> sliceSamples = new ArrayList<>();
            for (PlanCenterlineSample sample : roadSamples) {
                if (sample.canonicalStation() >= start - STATION_EPSILON
                        && sample.canonicalStation() <= end + STATION_EPSILON) {
                    sliceSamples.add(sample);
                }
            }
            if (!slice.forward()) {
                Collections.reverse(sliceSamples);
            }
            filtered.addAll(sliceSamples);
        }
        return List.copyOf(filtered);
    }
}
