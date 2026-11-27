package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.RoadTopologyViolation;
import com.plot.plugin.road.model.RoadTopologyViolationKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Road-local 里程（chainage）权威坐标转换器：沿有序分段链累计弧长，并处理分段方向。
 * <p>
 * <strong>Canonical chainage 域</strong>：{@link #canonicalLength} — 工程桩号权威上界
 * （有有效 HA 时取 {@link #designLength}，否则取 {@link #instanceLength}）。
 * 所有 {@link RoadStation}、VA/VCS/设施桩号、{@link #isValid} 均在此域。
 * <p>
 * <strong>Design length</strong>：{@link #designLength} — {@link com.plot.plugin.road.alignment.RoadHorizontalAlignment}
 * 线形总长（无 HA 时为 0）。
 * <p>
 * <strong>Instance chainage 域</strong>：{@link #instanceLength} — {@link com.plot.plugin.road.model.RoadEdge}
 * 派生折线累计弧长；{@link OrientedRoadSegment} 内部几何换算仍基于此域，对外 API 自动换算。
 * <p>
 * 仅对拓扑可维护的道路（无分叉、无断开）保证语义；分叉/断开时 {@link #isStationable} 为 false。
 * <p>
 * Phase 2 沿程模块应通过本类换算桩号，禁止自行拼接 {@code segmentStart + localDistance}。
 *
 * @see OrientedRoadSegment
 * @see docs/development/task-assignments/RoadSystemPlugin_Phase2_Stationing_v1.md
 */
public final class RoadStationing {

    private static final double STATION_EPSILON = 1e-6;

    private RoadStationing() {
    }

    public static List<String> orderedSegments(RoadNetwork network, Road road) {
        return RoadSegmentOrdering.orderedSegmentIds(network, road);
    }

    /**
     * 带方向的沿程分段列表；道路链的唯一拓扑+方向真相。
     */
    public static List<OrientedRoadSegment> orientedSegments(RoadNetwork network, Road road) {
        return buildOrientedSegments(network, road, true);
    }

    static List<OrientedRoadSegment> orientedSegmentsWithoutLoopRotation(RoadNetwork network, Road road) {
        return buildOrientedSegments(network, road, false);
    }

    /**
     * 物理 Edge 写回用语义：不应用 LOOP seam slice 拆分，每个 edgeId 至多出现一次。
     * When HA is present on a LOOP, the chain is rotated so physical station 0 is at the
     * entry nearest the HA origin — required for a strictly monotonic physical↔native map.
     */
    public static List<OrientedRoadSegment> physicalEdgeSegmentsForMaterialization(
            RoadNetwork network,
            Road road) {
        // Same design-entry circulation as stationing; HA may rotate start (LOOP) or
        // reverse LINEAR walk — see {@link #isPhysicalWalkOpposedToDesign}.
        List<OrientedRoadSegment> base = buildLinearOrientedSegments(network, road, false, true);
        return rotatePhysicalChainToHaOrigin(network, road, base);
    }

    /**
     * Align physical materialization walk with design circulation when possible.
     * LOOP: rotate start to the design-walk node nearest HA origin (no circulation flip).
     * LINEAR: may reverse so physical 0 meets HA origin; callers must use
     * {@link #rotatedToPhysical} / {@link #physicalToRotated} which apply {@code L-s} when opposed.
     */
    static List<OrientedRoadSegment> rotatePhysicalChainToHaOrigin(
            RoadNetwork network,
            Road road,
            List<OrientedRoadSegment> segments) {
        if (network == null || road == null || segments == null || segments.isEmpty()) {
            return segments == null ? List.of() : segments;
        }

        if (!RoadPlanGeometry.hasDesignAlignment(network, road)) {
            return List.copyOf(segments);
        }
        Vec2d origin = road.getHorizontalAlignment().getOrigin();
        if (origin == null) {
            return List.copyOf(segments);
        }

        List<OrientedRoadSegment> oriented = segments;
        if (road.getTopologyMode() == RoadTopologyMode.LOOP && segments.size() > 1) {
            int bestIndex = 0;
            double bestDist = Double.POSITIVE_INFINITY;
            for (int i = 0; i < segments.size(); i++) {
                RoadNode entry = network.getNode(segments.get(i).entryNodeId());
                if (entry == null || entry.getPosition() == null) {
                    continue;
                }
                double dist = entry.getPosition().distance(origin);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIndex = i;
                }
            }
            if (bestIndex > 0) {
                oriented = rotateSegments(segments, bestIndex);
            }
        } else {
            // LINEAR: reverse when HA origin is nearer the design exit (physical 0 ↔ HA 0).
            RoadNode entry = network.getNode(segments.getFirst().entryNodeId());
            RoadNode exit = network.getNode(segments.getLast().exitNodeId());
            if (entry != null && exit != null
                    && entry.getPosition() != null && exit.getPosition() != null
                    && exit.getPosition().distance(origin) + 1e-6 < entry.getPosition().distance(origin)) {
                oriented = reversePhysicalSegments(segments);
            }
        }

        // If still decreasing native, reverse circulation and rely on rotated↔physical L-s.
        if (firstSegmentDecreasesHaNative(network, road, oriented)) {
            oriented = reversePhysicalSegments(oriented);
        }
        return List.copyOf(oriented);
    }

    /**
     * True when the physical materialization walk circulates opposite the design walk
     * (LINEAR HA-origin flip, or LOOP reverse with optional start rotation).
     * Detected by per-edge {@code forward()} flip — not by list index mirror — so LOOP
     * reverse+rotate still counts as opposed while rotation-only does not.
     */
    public static boolean isPhysicalWalkOpposedToDesign(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return false;
        }
        List<OrientedRoadSegment> design = orientedSegmentsWithoutLoopRotation(network, road);
        List<OrientedRoadSegment> physical = physicalEdgeSegmentsForMaterialization(network, road);
        if (design.isEmpty() || design.size() != physical.size()) {
            return false;
        }
        Map<String, Boolean> designForward = new HashMap<>();
        for (OrientedRoadSegment segment : design) {
            designForward.put(segment.edgeId(), segment.forward());
        }
        Map<String, Boolean> physicalForward = new HashMap<>();
        for (OrientedRoadSegment segment : physical) {
            physicalForward.put(segment.edgeId(), segment.forward());
        }
        if (!designForward.keySet().equals(physicalForward.keySet())) {
            return false;
        }
        for (Map.Entry<String, Boolean> entry : designForward.entrySet()) {
            Boolean physicalDir = physicalForward.get(entry.getKey());
            if (physicalDir == null || physicalDir.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static List<OrientedRoadSegment> rotateSegments(List<OrientedRoadSegment> segments, int bestIndex) {
        List<OrientedRoadSegment> rotated = new ArrayList<>(segments.size());
        double station = 0.0;
        for (int k = 0; k < segments.size(); k++) {
            OrientedRoadSegment original = segments.get((bestIndex + k) % segments.size());
            rotated.add(original.withStartStation(station));
            station += original.length();
        }
        return rotated;
    }

    private static boolean firstSegmentDecreasesHaNative(
            RoadNetwork network,
            Road road,
            List<OrientedRoadSegment> segments) {
        if (segments.isEmpty()) {
            return false;
        }
        OrientedRoadSegment first = segments.getFirst();
        RoadEdge edge = network.getEdge(first.edgeId());
        if (edge == null || edge.getCenterlinePoints() == null || edge.getCenterlinePoints().size() < 2) {
            return false;
        }
        Vec2d entryWorld = RoadGeometryUtils.pointAtDistance(
            edge.getCenterlinePoints(), first.geometryLocalFromChainLocal(0.0));
        Vec2d probeWorld = RoadGeometryUtils.pointAtDistance(
            edge.getCenterlinePoints(),
            first.geometryLocalFromChainLocal(Math.min(0.5, Math.max(1e-3, first.length() * 0.05))));
        if (entryWorld == null || probeWorld == null) {
            return false;
        }
        Vec2d chainDir = probeWorld.subtract(entryWorld);
        if (chainDir.lengthSquared() < 1e-8) {
            return false;
        }
        double chainBearing = Math.atan2(chainDir.y, chainDir.x);
        double haBearing = road.getHorizontalAlignment().getStartBearingRadians();
        double delta = Math.abs(Math.atan2(
            Math.sin(chainBearing - haBearing),
            Math.cos(chainBearing - haBearing)));
        // First physical step should follow HA start bearing when the walk begins at HA origin.
        return delta > Math.PI * 0.5;
    }

    private static List<OrientedRoadSegment> reversePhysicalSegments(List<OrientedRoadSegment> segments) {
        List<OrientedRoadSegment> reversed = new ArrayList<>(segments.size());
        double station = 0.0;
        for (int i = segments.size() - 1; i >= 0; i--) {
            OrientedRoadSegment original = segments.get(i);
            reversed.add(new OrientedRoadSegment(
                original.edgeId(),
                !original.forward(),
                original.exitNodeId(),
                original.entryNodeId(),
                station,
                original.length(),
                0.0,
                original.length(),
                original.fullEdgeLength() > 0.0 ? original.fullEdgeLength() : original.length()));
            station += original.length();
        }
        return reversed;
    }

    /**
     * 单条物理 Edge 在未旋转链上的定向分段。
     */
    public static Optional<OrientedRoadSegment> physicalOrientedSegment(
            RoadNetwork network,
            Road road,
            String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return Optional.empty();
        }
        for (OrientedRoadSegment segment : physicalEdgeSegmentsForMaterialization(network, road)) {
            if (segment.edgeId().equals(edgeId)) {
                return Optional.of(segment);
            }
        }
        return Optional.empty();
    }

    private static List<OrientedRoadSegment> buildOrientedSegments(
            RoadNetwork network,
            Road road,
            boolean applyLoopSeam) {
        var cache = RoadStationingReadCache.activeFor(network);
        if (cache != null && road != null) {
            return cache.segments(road.getId(), applyLoopSeam,
                () -> buildOrientedSegmentsUncached(network, road, applyLoopSeam));
        }
        return buildOrientedSegmentsUncached(network, road, applyLoopSeam);
    }

    private static List<OrientedRoadSegment> buildOrientedSegmentsUncached(
            RoadNetwork network, Road road, boolean applyLoopSeam) {
        if (network == null || road == null) {
            return List.of();
        }
        if (applyLoopSeam
                && road.getTopologyMode() == RoadTopologyMode.LOOP
                && road.getLoopSeam() != null) {
            return RoadLoopSeamService.buildLoopOrientedSegments(network, road);
        }
        return buildLinearOrientedSegments(network, road, applyLoopSeam);
    }

    static List<OrientedRoadSegment> buildBaseOrientedSegments(RoadNetwork network, Road road) {
        return buildLinearOrientedSegments(network, road, false, true);
    }

    private static List<OrientedRoadSegment> buildLinearOrientedSegments(
            RoadNetwork network,
            Road road,
            boolean applyLoopSeam) {
        return buildLinearOrientedSegments(network, road, applyLoopSeam, true);
    }

    private static List<OrientedRoadSegment> buildLinearOrientedSegments(
            RoadNetwork network,
            Road road,
            boolean applyLoopSeam,
            boolean useDesignEntryEdge) {
        List<OrientedRoadSegment> oriented = new ArrayList<>();
        double station = 0.0;
        for (SegmentChainBinding binding : segmentChainBindings(
                network, road, applyLoopSeam, useDesignEntryEdge)) {
            RoadEdge edge = network.getEdge(binding.segmentId());
            if (edge == null) {
                continue;
            }
            boolean forward = binding.entryNodeId().equals(edge.getStartNodeId());
            String exitNodeId = forward ? edge.getEndNodeId() : edge.getStartNodeId();
            double edgeLength = edge.getLength();
            oriented.add(new OrientedRoadSegment(
                binding.segmentId(),
                forward,
                binding.entryNodeId(),
                exitNodeId,
                station,
                edgeLength,
                0.0,
                edgeLength,
                edgeLength));
            station += edgeLength;
        }
        return List.copyOf(oriented);
    }

    public static Optional<OrientedRoadSegment> orientedSegment(
            RoadNetwork network,
            Road road,
            String segmentId) {
        List<OrientedRoadSegment> matches = orientedSegmentsForEdge(network, road, segmentId);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    /**
     * 返回 edgeId 对应的全部 traversal slice（LOOP interior seam 可能拆成两段）。
     */
    public static List<OrientedRoadSegment> orientedSegmentsForEdge(
            RoadNetwork network,
            Road road,
            String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return List.of();
        }
        List<OrientedRoadSegment> matches = new ArrayList<>();
        for (OrientedRoadSegment segment : orientedSegments(network, road)) {
            if (segment.edgeId().equals(edgeId)) {
                matches.add(segment);
            }
        }
        return List.copyOf(matches);
    }

    /**
     * Canonical 道路链长（工程桩号域权威上界）。
     *
     * @see RoadPlanGeometry#canonicalLength
     */
    public static double canonicalLength(RoadNetwork network, Road road) {
        return RoadPlanGeometry.canonicalLength(network, road);
    }

    /**
     * 设计平面线形总长。
     *
     * @see RoadPlanGeometry#designLength
     */
    public static double designLength(RoadNetwork network, Road road) {
        return RoadPlanGeometry.designLength(network, road);
    }

    /**
     * 实例折线链长（{@link RoadEdge} 派生几何累计弧长）。
     *
     * @see RoadPlanGeometry#instanceLength
     */
    public static double instanceLength(RoadNetwork network, Road road) {
        return RoadPlanGeometry.instanceLength(network, road);
    }

    public static Optional<String> chainEntryNodeId(RoadNetwork network, Road road) {
        List<OrientedRoadSegment> segments = orientedSegments(network, road);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(segments.getFirst().entryNodeId());
    }

    public static Optional<String> chainExitNodeId(RoadNetwork network, Road road) {
        List<OrientedRoadSegment> segments = orientedSegments(network, road);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(segments.getLast().exitNodeId());
    }

    /**
     * 道路是否具备稳定桩号语义（连通、无分叉；LINEAR 或 LOOP）。
     */
    public static boolean isStationable(RoadNetwork network, Road road) {
        var cache = RoadStationingReadCache.activeFor(network);
        if (cache != null && road != null) {
            return cache.stationable(road.getId(), () -> isStationableUncached(network, road));
        }
        return isStationableUncached(network, road);
    }

    private static boolean isStationableUncached(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return false;
        }
        if (network.getRoad(road.getId()) == null) {
            return false;
        }
        for (RoadTopologyViolation violation : RoadTopologyInvariantValidator.validateRoad(network, road)) {
            if (violation.kind() == RoadTopologyViolationKind.ROAD_DISCONNECTED
                    || violation.kind() == RoadTopologyViolationKind.ROAD_BRANCHING
                    || violation.kind() == RoadTopologyViolationKind.ROAD_LOOP_OPEN) {
                return false;
            }
            if (violation.kind() == RoadTopologyViolationKind.ROAD_CYCLE
                    && road.getTopologyMode() != RoadTopologyMode.LOOP) {
                return false;
            }
        }
        List<OrientedRoadSegment> segments = orientedSegments(network, road);
        if (segments.isEmpty()) {
            return false;
        }
        double length = 0.0;
        for (OrientedRoadSegment segment : segments) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                return false;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            if (points == null || points.size() < 2) {
                return false;
            }
            for (Vec2d point : points) {
                if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y)) {
                    return false;
                }
            }
            if (!Double.isFinite(segment.length()) || segment.length() < 0) {
                return false;
            }
            length += segment.length();
        }
        if (!(length > STATION_EPSILON)) {
            return false;
        }
        if (road.getTopologyMode() == RoadTopologyMode.LOOP && road.getLoopSeam() != null) {
            return RoadLoopSeamService.isSeamResolvable(network, road);
        }
        return true;
    }

    public static boolean isValid(RoadNetwork network, RoadStation station) {
        if (station == null) {
            return false;
        }
        Road road = network != null ? network.getRoad(station.roadId()) : null;
        if (!isStationable(network, road)) {
            return false;
        }
        double total = canonicalLength(network, road);
        return station.chainageMeters() >= -STATION_EPSILON
            && station.chainageMeters() <= total + STATION_EPSILON;
    }

    public static boolean segmentFlowsWithGeometry(RoadNetwork network, Road road, String segmentId) {
        return orientedSegment(network, road, segmentId)
            .map(OrientedRoadSegment::forward)
            .orElse(true);
    }

    public static OptionalDouble stationAtNode(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            String nodeId) {
        Optional<OrientedRoadSegment> oriented = orientedSegment(network, road, edge.getId());
        if (oriented.isEmpty()) {
            return OptionalDouble.empty();
        }
        OptionalDouble station = oriented.get().roadStationAtNode(nodeId);
        if (station.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(toCanonicalChainage(network, road, station.getAsDouble()));
    }

    /**
     * 道路链起点（首段入口节点）世界坐标。
     */
    public static Optional<Vec2d> chainOrigin(RoadNetwork network, Road road) {
        List<OrientedRoadSegment> segments = orientedSegments(network, road);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        RoadNode node = network.getNode(segments.getFirst().entryNodeId());
        if (node == null || node.getPosition() == null) {
            return Optional.empty();
        }
        return Optional.of(node.getPosition().copy());
    }

    /**
     * 道路桩号处平面坐标；有设计平面线形时读 {@link RoadPlanGeometry}，否则读实例折线。
     */
    public static Optional<Vec2d> pointAtStation(RoadNetwork network, Road road, double chainageMeters) {
        return RoadPlanGeometry.pointAtStation(network, road, chainageMeters);
    }

    /**
     * 道路桩号处实例折线平面坐标（忽略设计线形）。
     */
    public static Optional<Vec2d> instancePointAtStation(RoadNetwork network, Road road, double chainageMeters) {
        return RoadPlanGeometry.instancePointAtStation(network, road, chainageMeters);
    }

    public static double segmentStartStation(RoadNetwork network, Road road, String segmentId) {
        return orientedSegment(network, road, segmentId)
            .map(segment -> toCanonicalChainage(network, road, segment.startStation()))
            .orElse(-1.0);
    }

    /**
     * 世界坐标 → canonical 桩号；基于 plan 中心线与 {@link OrientedRoadSegment} 链方向。
     * <p>
     * 边存储反向（{@code forward == false}）时仍返回同一物理位置的桩号，与
     * {@link com.plot.plugin.road.station.RoadOrientationInvariantTest} 一致。
     */
    public static OptionalDouble chainageAtPosition(RoadNetwork network, Road road, Vec2d position) {
        return chainageAtPosition(network, road, position, null);
    }

    /**
     * 世界坐标 → canonical 桩号；可选传入边采样缓存（同一次 Crossing 检测内复用）。
     */
    public static OptionalDouble chainageAtPosition(
            RoadNetwork network,
            Road road,
            Vec2d position,
            java.util.Map<String, List<PlanCenterlineSample>> cachedSamplesByRoadId) {
        if (network == null || road == null || position == null || network.getRoad(road.getId()) == null) {
            return OptionalDouble.empty();
        }
        if (RoadPlanGeometry.hasDesignAlignment(network, road)) {
            return chainageAtPositionOnDesignAlignment(network, road, position, cachedSamplesByRoadId);
        }
        return chainageAtPositionOnInstanceCenterline(network, road, position);
    }

    private static OptionalDouble chainageAtPositionOnDesignAlignment(
            RoadNetwork network,
            Road road,
            Vec2d position,
            java.util.Map<String, List<PlanCenterlineSample>> cachedSamplesByRoadId) {
        List<PlanCenterlineSample> samples = cachedSamplesByRoadId != null
            ? cachedSamplesByRoadId.computeIfAbsent(
                road.getId(), id -> RoadPlanGeometry.resolveRoadCenterlineSamples(network, road))
            : RoadPlanGeometry.resolveRoadCenterlineSamples(network, road);
        OptionalDouble chainage = RoadPlanGeometry.chainageAtPositionOnPlanSamples(position, samples);
        if (chainage.isEmpty()) {
            return OptionalDouble.empty();
        }
        double distance = distanceToPlanSamples(position, samples);
        if (distance > RoadNetworkBuilder.NODE_TOLERANCE) {
            return OptionalDouble.empty();
        }
        return chainage;
    }

    private static double distanceToPlanSamples(Vec2d position, List<PlanCenterlineSample> samples) {
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < samples.size() - 1; i++) {
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(
                samples.get(i).position(), samples.get(i + 1).position(), position);
            bestDistance = Math.min(bestDistance, projected.distance(position));
        }
        return bestDistance;
    }

    private static OptionalDouble chainageAtPositionOnInstanceCenterline(
            RoadNetwork network,
            Road road,
            Vec2d position) {
        double bestDistance = Double.MAX_VALUE;
        Double bestChainage = null;
        for (OrientedRoadSegment segment : orientedSegments(network, road)) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                continue;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d start = points.get(i);
                Vec2d end = points.get(i + 1);
                Vec2d projected = RoadGeometryUtils.projectPointOnSegment(start, end, position);
                double distance = projected.distance(position);
                if (distance > RoadNetworkBuilder.NODE_TOLERANCE || distance >= bestDistance) {
                    continue;
                }
                double geometryLocal = start.distance(projected);
                for (int j = 0; j < i; j++) {
                    geometryLocal += points.get(j).distance(points.get(j + 1));
                }
                if (!segment.containsGeometryLocal(geometryLocal)) {
                    continue;
                }
                bestDistance = distance;
                double instanceChainage = segment.roadStationAtGeometryLocal(geometryLocal);
                double canonical = toCanonicalChainage(network, road, instanceChainage);
                if (!Double.isFinite(canonical)) {
                    continue;
                }
                bestChainage = canonical;
            }
        }
        return bestChainage != null ? OptionalDouble.of(bestChainage) : OptionalDouble.empty();
    }

    public static Optional<RoadStation> stationAt(
            RoadNetwork network,
            Road road,
            String segmentId,
            double geometryLocalDistance) {
        if (!isStationable(network, road) || segmentId == null || segmentId.isBlank()) {
            return Optional.empty();
        }
        if (!Double.isFinite(geometryLocalDistance)) {
            return Optional.empty();
        }
        RoadEdge edge = network.getEdge(segmentId);
        if (edge == null) {
            return Optional.empty();
        }
        if (geometryLocalDistance < -STATION_EPSILON
                || geometryLocalDistance > edge.getLength() + STATION_EPSILON) {
            return Optional.empty();
        }
        // LOOP interior seam may split one edge into two slices — pick the slice that
        // actually contains this local distance; never clamp into the wrong slice.
        OrientedRoadSegment oriented = null;
        for (OrientedRoadSegment candidate : orientedSegmentsForEdge(network, road, segmentId)) {
            if (candidate.containsGeometryLocal(geometryLocalDistance)) {
                oriented = candidate;
                break;
            }
        }
        if (oriented == null) {
            return Optional.empty();
        }
        double instanceChainage = oriented.roadStationAtGeometryLocal(geometryLocalDistance);
        double chainage = toCanonicalChainage(network, road, instanceChainage);
        if (!Double.isFinite(chainage) || !isValid(network, new RoadStation(road.getId(), chainage))) {
            return Optional.empty();
        }
        return Optional.of(new RoadStation(road.getId(), chainage));
    }

    /**
     * Canonical station at an edge geometry-local distance, selecting the correct LOOP slice
     * when an interior seam splits the edge (never clamps into {@code orientations.getFirst()}).
     */
    public static OptionalDouble resolveCanonicalStationAtEdgeLocal(
            RoadNetwork network,
            Road road,
            String edgeId,
            double geometryLocalDistance) {
        Optional<RoadStation> station = stationAt(network, road, edgeId, geometryLocalDistance);
        return station.isPresent()
            ? OptionalDouble.of(station.get().chainageMeters())
            : OptionalDouble.empty();
    }

    /**
     * 道路桩号 → 边内几何局部距离。
     */
    public static Optional<SegmentStation> edgeLocalDistanceAtRoadStation(
            RoadNetwork network,
            Road road,
            double chainageMeters) {
        return resolve(network, road, chainageMeters);
    }

    public static Optional<SegmentStation> resolve(RoadNetwork network, Road road, double chainageMeters) {
        if (!isStationable(network, road) || !Double.isFinite(chainageMeters)) {
            return Optional.empty();
        }
        if (!isValid(network, new RoadStation(road.getId(), chainageMeters))) {
            return Optional.empty();
        }

        double instanceChainage = toInstanceChainage(network, road, chainageMeters);
        List<OrientedRoadSegment> segments = orientedSegments(network, road);
        for (int i = 0; i < segments.size(); i++) {
            OrientedRoadSegment segment = segments.get(i);
            boolean isLast = i == segments.size() - 1;
            if (instanceChainage < segment.endStation() - STATION_EPSILON || isLast) {
                OptionalDouble geometryLocal = segment.geometryLocalAtRoadStation(instanceChainage);
                if (geometryLocal.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new SegmentStation(segment.edgeId(), geometryLocal.getAsDouble()));
            }
        }
        return Optional.empty();
    }

    public static String format(double chainageMeters, RoadStationFormat format) {
        if (!Double.isFinite(chainageMeters)) {
            return "-";
        }
        if (format == RoadStationFormat.DISTANCE_METERS) {
            return formatDistanceMeters(chainageMeters);
        }
        int kilometers = (int) Math.floor(chainageMeters / 1000.0);
        double meters = chainageMeters - kilometers * 1000.0;
        if (Math.abs(meters - Math.rint(meters)) < 0.05) {
            meters = Math.rint(meters);
            if (format == RoadStationFormat.KILOMETER_PLUS) {
                return String.format(Locale.ROOT, "K%d+%03.0f", kilometers, meters);
            }
            return String.format(Locale.ROOT, "%d+%03.0f", kilometers, meters);
        }
        if (format == RoadStationFormat.KILOMETER_PLUS) {
            return String.format(Locale.ROOT, "K%d+%06.2f", kilometers, meters);
        }
        return String.format(Locale.ROOT, "%d+%06.2f", kilometers, meters);
    }

    public static String format(
            double chainageMeters,
            double totalLengthMeters,
            RoadStationFormat format,
            ChainageDisplayMode displayMode) {
        if (displayMode == ChainageDisplayMode.FROM_END) {
            double fromEnd = Math.max(0.0, totalLengthMeters - chainageMeters);
            return formatFromEnd(fromEnd, format);
        }
        return format(chainageMeters, format);
    }

    public static String formatFromEnd(double metersFromEnd, RoadStationFormat format) {
        if (!Double.isFinite(metersFromEnd)) {
            return "-";
        }
        if (format == RoadStationFormat.DISTANCE_METERS) {
            return formatDistanceMeters(metersFromEnd);
        }
        int kilometers = (int) Math.floor(metersFromEnd / 1000.0);
        double meters = metersFromEnd - kilometers * 1000.0;
        if (Math.abs(meters - Math.rint(meters)) < 0.05) {
            meters = Math.rint(meters);
            if (format == RoadStationFormat.KILOMETER_PLUS) {
                return String.format(Locale.ROOT, "EK%d+%03.0f", kilometers, meters);
            }
            return String.format(Locale.ROOT, "E%d+%03.0f", kilometers, meters);
        }
        if (format == RoadStationFormat.KILOMETER_PLUS) {
            return String.format(Locale.ROOT, "EK%d+%06.2f", kilometers, meters);
        }
        return String.format(Locale.ROOT, "E%d+%06.2f", kilometers, meters);
    }

    public static String format(RoadStation station, RoadStationFormat format) {
        if (station == null) {
            return "-";
        }
        return format(station.chainageMeters(), format);
    }

    /**
     * Seam arc length on the physical materialization chain
     * ({@link #physicalEdgeSegmentsForMaterialization}).
     */
    public static double physicalSeamStation(RoadNetwork network, Road road) {
        if (network == null || road == null || road.getLoopSeam() == null) {
            return Double.NaN;
        }
        var seam = road.getLoopSeam();
        List<OrientedRoadSegment> physical = physicalEdgeSegmentsForMaterialization(network, road);
        if (seam.segmentHintId() != null && seam.localFraction() != null) {
            for (OrientedRoadSegment segment : physical) {
                if (!segment.edgeId().equals(seam.segmentHintId())) {
                    continue;
                }
                RoadEdge edge = network.getEdge(segment.edgeId());
                double geometryLength = edge != null ? edge.getLength() : segment.length();
                // localFraction is along storage geometry; convert through chain orientation.
                double geometryLocal = seam.localFraction() * geometryLength;
                double chainLocal = segment.chainLocalFromGeometryLocal(geometryLocal);
                return segment.roadStationAtChainLocal(chainLocal);
            }
        }
        RoadLoopSeamService.SeamProjection projection =
            RoadLoopSeamService.resolveFromSeam(network, road, seam);
        if (projection == null) {
            return Double.NaN;
        }
        for (OrientedRoadSegment segment : physical) {
            if (!segment.edgeId().equals(projection.segmentId())) {
                continue;
            }
            RoadEdge edge = network.getEdge(segment.edgeId());
            double geometryLength = edge != null ? edge.getLength() : segment.length();
            // geometryLocalFraction is storage-geometry; convert onto the physical walk.
            double geometryLocal = projection.geometryLocalFraction() * geometryLength;
            double chainLocal = segment.chainLocalFromGeometryLocal(geometryLocal);
            return segment.roadStationAtChainLocal(chainLocal);
        }
        return Double.NaN;
    }

    /**
     * Oriented/rotated instance ↔ physical instance.
     * LOOP+seam: {@code physical = wrap(seam + σ·rotated, L)} via {@link CyclicStationTransform}.
     */
    public static double rotatedToPhysical(RoadNetwork network, Road road, double rotatedInstance) {
        if (!Double.isFinite(rotatedInstance) || network == null || road == null) {
            return Double.NaN;
        }
        double physicalTotal = physicalInstanceLength(network, road);
        if (!(physicalTotal > STATION_EPSILON)) {
            return Double.NaN;
        }
        int sigma = isPhysicalWalkOpposedToDesign(network, road) ? -1 : 1;
        if (road.getTopologyMode() != RoadTopologyMode.LOOP) {
            double station = sigma < 0 ? physicalTotal - rotatedInstance : rotatedInstance;
            return clampStation(station, physicalTotal);
        }
        // LOOP: explicit seam or virtual design-entry origin (physical 0).
        double seam = road.getLoopSeam() != null ? physicalSeamStation(network, road) : 0.0;
        if (!Double.isFinite(seam)) {
            return Double.NaN;
        }
        double physical = CyclicStationTransform.fromOrigin(seam, sigma, rotatedInstance, physicalTotal);
        return Double.isFinite(physical) ? clampStation(physical, physicalTotal) : Double.NaN;
    }

    public static double physicalToRotated(RoadNetwork network, Road road, double physicalInstance) {
        if (!Double.isFinite(physicalInstance) || network == null || road == null) {
            return Double.NaN;
        }
        double physicalTotal = physicalInstanceLength(network, road);
        if (!(physicalTotal > STATION_EPSILON)) {
            return Double.NaN;
        }
        int sigma = isPhysicalWalkOpposedToDesign(network, road) ? -1 : 1;
        if (road.getTopologyMode() != RoadTopologyMode.LOOP) {
            double station = sigma < 0 ? physicalTotal - physicalInstance : physicalInstance;
            return clampStation(station, physicalTotal);
        }
        double seam = road.getLoopSeam() != null ? physicalSeamStation(network, road) : 0.0;
        if (!Double.isFinite(seam)) {
            return Double.NaN;
        }
        double rotated = CyclicStationTransform.toOrigin(seam, sigma, physicalInstance, physicalTotal);
        return Double.isFinite(rotated) ? clampStation(rotated, physicalTotal) : Double.NaN;
    }

    private static double clampStation(double station, double total) {
        if (station < 0.0) {
            return 0.0;
        }
        if (station > total) {
            return total;
        }
        return station;
    }

    private static double physicalInstanceLength(RoadNetwork network, Road road) {
        double length = 0.0;
        for (OrientedRoadSegment segment : physicalEdgeSegmentsForMaterialization(network, road)) {
            length += segment.length();
        }
        return length;
    }

    /**
     * Canonical 桩号 → oriented/rotated 实例折线链上弧长。
     * 路径：canonical → HA native → physical → rotated。非法输入返回 {@link Double#NaN}。
     */
    public static double toInstanceChainage(RoadNetwork network, Road road, double canonicalChainage) {
        if (!Double.isFinite(canonicalChainage) || network == null || road == null) {
            return Double.NaN;
        }
        // Without HA, oriented/rotated instance IS the canonical domain.
        if (!RoadPlanGeometry.hasDesignAlignment(network, road)) {
            return canonicalChainage;
        }
        StationDomainMap map = domainMap(network, road);
        if (map == null || !map.isReliable()) {
            return Double.NaN;
        }
        double instanceTotal = RoadPlanGeometry.instanceLength(network, road);
        double canonicalTotal = RoadPlanGeometry.canonicalLength(network, road);
        boolean loopSeam = road.getTopologyMode() == RoadTopologyMode.LOOP && road.getLoopSeam() != null;
        if (loopSeam && Math.abs(canonicalChainage) <= STATION_EPSILON) {
            return 0.0;
        }
        if (loopSeam && Math.abs(canonicalChainage - canonicalTotal) <= STATION_EPSILON) {
            return instanceTotal;
        }
        double nativeStation = RoadPlanGeometry.haNativeStation(network, road, canonicalChainage);
        if (!Double.isFinite(nativeStation)) {
            return Double.NaN;
        }
        double physical = map.toPhysical(nativeStation);
        if (!Double.isFinite(physical)) {
            return Double.NaN;
        }
        return physicalToRotated(network, road, physical);
    }

    /**
     * Oriented/rotated 实例弧长 → canonical 桩号。
     * 路径：rotated → physical → HA native → canonical。非法输入返回 {@link Double#NaN}。
     */
    public static double toCanonicalChainage(RoadNetwork network, Road road, double instanceChainage) {
        if (!Double.isFinite(instanceChainage) || network == null || road == null) {
            return Double.NaN;
        }
        // Without HA, oriented/rotated instance IS the canonical domain.
        if (!RoadPlanGeometry.hasDesignAlignment(network, road)) {
            return instanceChainage;
        }
        StationDomainMap map = domainMap(network, road);
        if (map == null || !map.isReliable()) {
            return Double.NaN;
        }
        double instanceTotal = RoadPlanGeometry.instanceLength(network, road);
        double canonicalTotal = RoadPlanGeometry.canonicalLength(network, road);
        boolean loopSeam = road.getTopologyMode() == RoadTopologyMode.LOOP && road.getLoopSeam() != null;
        if (loopSeam && Math.abs(instanceChainage) <= STATION_EPSILON) {
            return 0.0;
        }
        if (loopSeam && Math.abs(instanceChainage - instanceTotal) <= STATION_EPSILON) {
            return canonicalTotal;
        }
        double physical = rotatedToPhysical(network, road, instanceChainage);
        if (!Double.isFinite(physical)) {
            return Double.NaN;
        }
        double nativeStation = map.toNative(physical);
        if (!Double.isFinite(nativeStation)) {
            return Double.NaN;
        }
        return RoadPlanGeometry.canonicalFromHaNative(network, road, nativeStation, map.nativeTotal());
    }

    public static StationDomainMap domainMap(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return StationDomainMap.identity(0.0);
        }
        var cache = RoadStationingReadCache.activeFor(network);
        if (cache != null) {
            return cache.domainMap(road.getId(), () -> StationDomainMap.build(network, road));
        }
        return StationDomainMap.build(network, road);
    }

    private record SegmentChainBinding(String segmentId, String entryNodeId) {
    }

    private static List<SegmentChainBinding> segmentChainBindings(
            RoadNetwork network,
            Road road,
            boolean applyLoopSeam,
            boolean useDesignEntryEdge) {
        if (network == null || road == null) {
            return List.of();
        }
        List<String> segmentIds = orderedSegments(network, road);
        if (segmentIds.isEmpty()) {
            return List.of();
        }

        Map<String, RoadEdge> edgesById = new HashMap<>();
        for (String segmentId : segmentIds) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge != null) {
                edgesById.put(segmentId, edge);
            }
        }
        if (edgesById.isEmpty()) {
            return List.of();
        }

        RoadDesignDirectionAuthority.ResolvedStart resolved =
            RoadDesignDirectionAuthority.resolveStart(network, road);
        String startNodeId = resolved.entryNodeId();
        String preferredEdgeId = useDesignEntryEdge ? resolved.preferredEntryEdgeId() : null;
        if (applyLoopSeam
                && road.getTopologyMode() == com.plot.plugin.road.model.RoadTopologyMode.LOOP
                && road.getLoopSeam() != null) {
            String seamStart = RoadLoopSeamService.resolveChainStartNodeId(network, road, road.getLoopSeam());
            if (seamStart != null && resolved.nodeToEdgeIds().containsKey(seamStart)) {
                startNodeId = seamStart;
                preferredEdgeId = null; // seam rotates start; edge preference is for design walk
            }
        }
        if (startNodeId == null) {
            return List.of();
        }

        List<SegmentChainBinding> bindings = new ArrayList<>(edgesById.size());
        Set<String> visited = new HashSet<>();
        var adjacency = resolved.nodeToEdgeIds();
        String currentNodeId = startNodeId;
        String currentEdgeId = RoadDesignDirectionAuthority.firstEdgeAtNode(
            adjacency, currentNodeId, preferredEdgeId, visited);

        while (currentEdgeId != null) {
            visited.add(currentEdgeId);
            bindings.add(new SegmentChainBinding(currentEdgeId, currentNodeId));
            RoadEdge edge = edgesById.get(currentEdgeId);
            if (edge == null) {
                break;
            }
            String nextNodeId = edge.getStartNodeId().equals(currentNodeId)
                ? edge.getEndNodeId()
                : edge.getStartNodeId();
            currentNodeId = nextNodeId;
            currentEdgeId = RoadDesignDirectionAuthority.firstEdgeAtNode(
                adjacency, currentNodeId, null, visited);
        }
        return bindings;
    }

    private static String formatDistanceMeters(double meters) {
        double safeMeters = Math.max(0.0, meters);
        if (safeMeters >= 1000.0) {
            return String.format(Locale.ROOT, "%.2f km", safeMeters / 1000.0);
        }
        return String.format(Locale.ROOT, "%.2f m", safeMeters);
    }
}
