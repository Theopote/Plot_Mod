package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.SegmentStation;
import com.plot.plugin.road.station.StationDomainMap;

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


    public static Optional<Double> bearingAtStation(RoadNetwork network, Road road, double chainageMeters) {
        if (hasDesignAlignment(network, road)) {
            Optional<Double> haBearing = HorizontalAlignmentGeometry.poseAt(
                    road.getHorizontalAlignment(),
                    haNativeStation(network, road, chainageMeters))
                .map(AlignmentPose::bearingRadians);
            if (haBearing.isEmpty()) {
                return Optional.empty();
            }
            // Opposed HA walk: increasing canonical follows design entry→exit = HA tangent + π.
            if (RoadStationing.isPhysicalWalkOpposedToDesign(network, road)) {
                double flipped = haBearing.get() + Math.PI;
                return Optional.of(Math.atan2(Math.sin(flipped), Math.cos(flipped)));
            }
            return haBearing;
        }
        return instanceBearingAtStation(network, road, chainageMeters);
    }

    /**
     * Point on the instance polyline at a <strong>canonical</strong> station
     * (converts via domain map). Mapping failure returns empty — no equal-length identity fallback.
     */
    public static Optional<Vec2d> instancePointAtStation(
            RoadNetwork network,
            Road road,
            double chainageMeters) {
        double instance = RoadStationing.toInstanceChainage(network, road, chainageMeters);
        if (!Double.isFinite(instance)) {
            return Optional.empty();
        }
        return instancePointAtInstanceChainage(network, road, instance);
    }

    /**
     * Point on the instance polyline at an <strong>instance</strong> chainage
     * (OrientedRoadSegment arc length — no domain-map conversion).
     */
    public static Optional<Vec2d> instancePointAtInstanceChainage(
            RoadNetwork network,
            Road road,
            double instanceChainage) {
        if (network == null || road == null || !Double.isFinite(instanceChainage)) {
            return Optional.empty();
        }
        List<OrientedRoadSegment> segments = RoadStationing.orientedSegments(network, road);
        for (int i = 0; i < segments.size(); i++) {
            OrientedRoadSegment segment = segments.get(i);
            boolean last = i == segments.size() - 1;
            if (instanceChainage < segment.startStation() - STATION_EPSILON) {
                continue;
            }
            if (!last && instanceChainage >= segment.endStation() - STATION_EPSILON) {
                continue;
            }
            if (last && instanceChainage > segment.endStation() + STATION_EPSILON) {
                continue;
            }
            double chainLocal = Math.max(0.0, Math.min(
                instanceChainage - segment.startStation(), segment.length()));
            double geometryLocal = segment.geometryLocalFromChainLocal(chainLocal);
            return instancePointAtEdgeLocal(network, road, segment.edgeId(), geometryLocal);
        }
        return Optional.empty();
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
        List<Double> stations = new ArrayList<>();
        stations.add(0.0);
        for (double chainage = spacing; chainage < total - STATION_EPSILON; chainage += spacing) {
            stations.add(chainage);
        }
        stations.add(total);
        // Polyline vertices → canonical via oriented instance stations.
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null || edge.getCenterlinePoints() == null) {
                continue;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            double walked = 0.0;
            for (int i = 0; i < points.size() - 1; i++) {
                walked += points.get(i).distance(points.get(i + 1));
                if (!segment.containsGeometryLocal(walked)) {
                    continue;
                }
                double instanceStation = segment.roadStationAtGeometryLocal(walked);
                double canonical = RoadStationing.toCanonicalChainage(network, road, instanceStation);
                if (Double.isFinite(canonical)
                        && canonical > STATION_EPSILON
                        && canonical < total - STATION_EPSILON) {
                    stations.add(canonical);
                }
            }
        }
        // HA element boundaries in canonical domain.
        if (hasDesignAlignment(network, road)) {
            RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
            double nativeWalked = 0.0;
            for (HorizontalAlignmentElement element : alignment.getElements()) {
                nativeWalked += element.getLength();
                if (nativeWalked <= STATION_EPSILON || nativeWalked >= designLength(network, road) - STATION_EPSILON) {
                    continue;
                }
                double canonical = canonicalFromHaNative(
                    network, road, nativeWalked, designLength(network, road));
                if (Double.isFinite(canonical)
                        && canonical > STATION_EPSILON
                        && canonical < total - STATION_EPSILON) {
                    stations.add(canonical);
                }
            }
        }
        stations.sort(Double::compareTo);
        List<PlanCenterlineSample> samples = new ArrayList<>();
        for (double chainage : stations) {
            appendRoadSampleIfDistinct(network, road, samples, chainage);
        }
        return List.copyOf(samples);
    }

    /**
     * 物理 Edge 的连续中心线采样（不按 LOOP seam slice 拆分）；用于物化写回。
     */
    public static List<PlanCenterlineSample> resolvePhysicalEdgeCenterlineSamples(
            RoadNetwork network,
            RoadEdge edge) {
        return resolvePhysicalEdgeCenterlineSamples(
            network,
            edge,
            HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
    }

    public static List<PlanCenterlineSample> resolvePhysicalEdgeCenterlineSamples(
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
        if (hasDesignAlignment(network, road)) {
            return RoadStationing.physicalOrientedSegment(network, road, edge.getId())
                .map(oriented -> HorizontalAlignmentCenterlineMaterializer.samplePlanCenterline(
                    network,
                    road,
                    road.getHorizontalAlignment(),
                    oriented,
                    sampleSpacingMeters))
                .orElse(List.of());
        }
        return samplePhysicalInstanceEdge(edge, sampleSpacingMeters);
    }

    /**
     * Instance-polyline samples along one physical Edge.
     * Station field stores <strong>geometryLocal</strong> (not canonical).
     * Includes exact start/end and original polyline vertices.
     */
    private static List<PlanCenterlineSample> samplePhysicalInstanceEdge(
            RoadEdge edge,
            double sampleSpacingMeters) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points == null || points.size() < 2) {
            return List.of();
        }
        double length = edge.getLength();
        if (!(length > STATION_EPSILON)) {
            return List.of();
        }
        double spacing = sampleSpacingMeters > STATION_EPSILON
            ? sampleSpacingMeters
            : HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS;
        List<Double> stations = new ArrayList<>();
        stations.add(0.0);
        double walked = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            walked += points.get(i).distance(points.get(i + 1));
            if (walked > STATION_EPSILON && walked < length - STATION_EPSILON) {
                stations.add(walked);
            }
        }
        for (double d = spacing; d < length - STATION_EPSILON; d += spacing) {
            stations.add(d);
        }
        stations.add(length);
        stations.sort(Double::compareTo);

        List<PlanCenterlineSample> samples = new ArrayList<>();
        for (double d : stations) {
            Vec2d point = RoadGeometryUtils.pointAtDistance(points, d);
            if (point == null) {
                continue;
            }
            if (samples.isEmpty()
                    || samples.getLast().position().distance(point) > STATION_EPSILON
                    || Math.abs(samples.getLast().canonicalStation() - d) > STATION_EPSILON) {
                if (!samples.isEmpty()
                        && samples.getLast().position().distance(point) <= STATION_EPSILON) {
                    samples.set(samples.size() - 1, PlanCenterlineSample.geometryLocal(point.copy(), d));
                } else {
                    samples.add(PlanCenterlineSample.geometryLocal(point.copy(), d));
                }
            }
        }
        return List.copyOf(samples);
    }

    /**
     * Road traversal 视角的 Edge 采样。
     * <p>
     * 单 slice 时返回一条连续折线（含准确起终点）。多 slice（LOOP interior seam）时
     * 请使用 {@link #resolveEdgeTraversalSliceSamples}，勿将不连续区间当一条折线。
     * 勿用于 {@link RoadEdge#setCenterlinePoints} 写回（用 {@link #resolvePhysicalEdgeCenterlineSamples}）。
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
        // Prefer physical HA-native sampling when design alignment is present (does not require
        // a reliable domain map for equal-length materialization ranges).
        if (hasDesignAlignment(network, road)) {
            return resolvePhysicalEdgeCenterlineSamples(network, edge, sampleSpacingMeters);
        }
        List<OrientedRoadSegment> slices = RoadStationing.orientedSegmentsForEdge(network, road, edge.getId());
        if (slices.isEmpty()) {
            return List.of();
        }
        if (slices.size() == 1) {
            return sampleContinuousSlice(
                network, road, slices.getFirst(), sampleSpacingMeters);
        }
        // Multi-slice: return physical continuous samples to avoid false joins across seam.
        return resolvePhysicalEdgeCenterlineSamples(network, edge, sampleSpacingMeters);
    }

    /**
     * One continuous sample list per traversal slice (each includes exact start/end).
     */
    public static List<List<PlanCenterlineSample>> resolveEdgeTraversalSliceSamples(
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
        List<List<PlanCenterlineSample>> out = new ArrayList<>(slices.size());
        for (OrientedRoadSegment slice : slices) {
            out.add(sampleContinuousSlice(network, road, slice, sampleSpacingMeters));
        }
        return List.copyOf(out);
    }

    private static List<PlanCenterlineSample> sampleContinuousSlice(
            RoadNetwork network,
            Road road,
            OrientedRoadSegment slice,
            double sampleSpacingMeters) {
        double spacing = sampleSpacingMeters > STATION_EPSILON
            ? sampleSpacingMeters
            : HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS;
        double start = RoadStationing.toCanonicalChainage(network, road, slice.startStation());
        double end = RoadStationing.toCanonicalChainage(network, road, slice.endStation());
        if (!Double.isFinite(start) || !Double.isFinite(end)) {
            // Do not mix instance into canonical — fail closed with empty samples.
            return List.of();
        }
        if (end < start) {
            double tmp = start;
            start = end;
            end = tmp;
        }
        List<Double> stations = new ArrayList<>();
        stations.add(start);
        for (double chainage = start + spacing; chainage < end - STATION_EPSILON; chainage += spacing) {
            stations.add(chainage);
        }
        stations.add(end);
        // Insert instance polyline vertices that fall inside this slice (shape fidelity).
        RoadEdge edge = network.getEdge(slice.edgeId());
        if (edge != null && edge.getCenterlinePoints() != null) {
            double walked = 0.0;
            List<Vec2d> points = edge.getCenterlinePoints();
            for (int i = 0; i < points.size() - 1; i++) {
                walked += points.get(i).distance(points.get(i + 1));
                if (!slice.containsGeometryLocal(walked)) {
                    continue;
                }
                double instanceStation = slice.roadStationAtGeometryLocal(walked);
                double canonical = RoadStationing.toCanonicalChainage(network, road, instanceStation);
                if (Double.isFinite(canonical)
                        && canonical > start + STATION_EPSILON
                        && canonical < end - STATION_EPSILON) {
                    stations.add(canonical);
                }
            }
        }
        stations.sort(Double::compareTo);
        List<PlanCenterlineSample> samples = new ArrayList<>();
        for (double chainage : stations) {
            appendRoadSampleIfDistinct(network, road, samples, chainage);
        }
        if (!slice.forward() && samples.size() > 1) {
            Collections.reverse(samples);
        }
        return List.copyOf(samples);
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

        // SegmentStation.localDistance is already from geometry storage start — do not mirror again.
        double clamped = Math.max(0.0, Math.min(geometryLocalDistance, edge.getLength()));
        Optional<double[]> tangent = tangentOnPolyline(points, clamped);
        if (tangent.isEmpty()) {
            return Optional.empty();
        }
        double dx = tangent.get()[0];
        double dy = tangent.get()[1];
        if (!oriented.get().forward()) {
            dx = -dx;
            dy = -dy;
        }
        if (dx * dx + dy * dy < STATION_EPSILON * STATION_EPSILON) {
            return Optional.empty();
        }
        return Optional.of(Math.atan2(dy, dx));
    }

    /**
     * Unitless tangent (dx, dy) of the polyline at geometry-local distance from storage start.
     */
    static Optional<double[]> tangentOnPolyline(List<Vec2d> points, double geometryLocal) {
        if (points == null || points.size() < 2 || !Double.isFinite(geometryLocal)) {
            return Optional.empty();
        }
        double remaining = Math.max(0.0, geometryLocal);
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            if (a == null || b == null) {
                continue;
            }
            double len = a.distance(b);
            if (len <= STATION_EPSILON) {
                continue;
            }
            if (remaining <= len + STATION_EPSILON) {
                return Optional.of(new double[] {b.x - a.x, b.y - a.y});
            }
            remaining -= len;
        }
        for (int i = points.size() - 2; i >= 0; i--) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            if (a == null || b == null) {
                continue;
            }
            double len = a.distance(b);
            if (len > STATION_EPSILON) {
                return Optional.of(new double[] {b.x - a.x, b.y - a.y});
            }
        }
        return Optional.empty();
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
     * Canonical (design-entry) station → HA native station.
     * LINEAR: identity unless opposed ({@code L - s}).
     * LOOP: {@code n = wrap(o + σ·s, L)} via {@link com.plot.plugin.road.station.CyclicStationTransform}.
     */
    public static double haNativeStation(RoadNetwork network, Road road, double canonicalStation) {
        if (road == null || !Double.isFinite(canonicalStation)) {
            return Double.NaN;
        }
        if (road.getTopologyMode() != RoadTopologyMode.LOOP) {
            double designLen = designLength(network, road);
            if (designLen > STATION_EPSILON
                    && RoadStationing.isPhysicalWalkOpposedToDesign(network, road)) {
                return Math.max(0.0, Math.min(designLen - canonicalStation, designLen));
            }
            return canonicalStation;
        }
        // LOOP (explicit or virtual design-entry seam): Cyclic o/σ contract — never LINEAR identity.
        double designLen = designLength(network, road);
        if (designLen <= STATION_EPSILON) {
            return Double.NaN;
        }
        double offset = seamHaNativeOffset(network, road);
        if (!Double.isFinite(offset)) {
            return Double.NaN;
        }
        int sigma = RoadStationing.isPhysicalWalkOpposedToDesign(network, road) ? -1 : 1;
        return com.plot.plugin.road.station.CyclicStationTransform.fromOrigin(
            offset, sigma, canonicalStation, designLen);
    }

    /** HA native → canonical (design-entry). LOOP uses {@code s = wrap(σ·(n − o), L)}. */
    public static double canonicalFromHaNative(
            RoadNetwork network,
            Road road,
            double nativeStation,
            double designTotal) {
        if (road == null || !Double.isFinite(nativeStation)) {
            return Double.NaN;
        }
        if (road.getTopologyMode() != RoadTopologyMode.LOOP) {
            double designLen = designTotal > STATION_EPSILON ? designTotal : designLength(network, road);
            if (designLen > STATION_EPSILON
                    && RoadStationing.isPhysicalWalkOpposedToDesign(network, road)) {
                return Math.max(0.0, Math.min(designLen - nativeStation, designLen));
            }
            return nativeStation;
        }
        double designLen = designTotal > STATION_EPSILON ? designTotal : designLength(network, road);
        if (designLen <= STATION_EPSILON) {
            return Double.NaN;
        }
        double offset = seamHaNativeOffset(network, road);
        if (!Double.isFinite(offset)) {
            return Double.NaN;
        }
        int sigma = RoadStationing.isPhysicalWalkOpposedToDesign(network, road) ? -1 : 1;
        return com.plot.plugin.road.station.CyclicStationTransform.toOrigin(
            offset, sigma, nativeStation, designLen);
    }

    public static double seamHaNativeOffset(RoadNetwork network, Road road) {
        var cache = com.plot.plugin.road.station.RoadStationingReadCache.activeFor(network);
        if (cache != null && road != null) {
            return cache.seamOffset(road.getId(), () -> seamHaNativeOffsetUncached(network, road));
        }
        return seamHaNativeOffsetUncached(network, road);
    }

    private static double seamHaNativeOffsetUncached(RoadNetwork network, Road road) {
        if (network == null || road == null
                || road.getTopologyMode() != RoadTopologyMode.LOOP) {
            return 0.0;
        }
        if (!hasDesignAlignment(network, road)) {
            return 0.0;
        }
        StationDomainMap map = RoadStationing.domainMap(network, road);
        if (map == null || !map.isReliable()) {
            return Double.NaN;
        }
        // Explicit seam: physical seam identity → HA native.
        // No seam: virtual origin at design-entry physical station (typically 0).
        double physicalSeam = road.getLoopSeam() != null
            ? RoadStationing.physicalSeamStation(network, road)
            : 0.0;
        if (!Double.isFinite(physicalSeam)) {
            return Double.NaN;
        }
        return map.toNative(physicalSeam, false);
    }

    public static List<PlanCenterlineSample> sampleHaNativeUnrotatedPublic(
            RoadHorizontalAlignment alignment,
            double sampleSpacingMeters) {
        return sampleHaNativeUnrotated(alignment, sampleSpacingMeters);
    }

    static List<PlanCenterlineSample> sampleHaNativeUnrotated(
            RoadHorizontalAlignment alignment,
            double sampleSpacingMeters) {
        if (alignment == null || alignment.isEmpty()) {
            return List.of();
        }
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
                    samples.add(PlanCenterlineSample.haNative(point, clamped));
                }
            });
        }
        HorizontalAlignmentGeometry.poseAt(alignment, total).ifPresent(pose -> {
            Vec2d point = new Vec2d(pose.x(), pose.y());
            if (samples.isEmpty()
                    || samples.getLast().position().distance(point) > STATION_EPSILON) {
                samples.add(PlanCenterlineSample.haNative(point, total));
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
                samples.add(PlanCenterlineSample.canonical(point, canonicalStation));
            }
        });
    }

}
