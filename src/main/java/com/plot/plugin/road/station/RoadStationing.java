package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
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
        if (network == null || road == null) {
            return List.of();
        }
        List<OrientedRoadSegment> oriented = new ArrayList<>();
        double station = 0.0;
        for (SegmentChainBinding binding : segmentChainBindings(network, road)) {
            RoadEdge edge = network.getEdge(binding.segmentId());
            if (edge == null) {
                continue;
            }
            boolean forward = binding.entryNodeId().equals(edge.getStartNodeId());
            String exitNodeId = forward ? edge.getEndNodeId() : edge.getStartNodeId();
            oriented.add(new OrientedRoadSegment(
                binding.segmentId(),
                forward,
                binding.entryNodeId(),
                exitNodeId,
                station,
                edge.getLength()));
            station += edge.getLength();
        }
        return List.copyOf(oriented);
    }

    public static Optional<OrientedRoadSegment> orientedSegment(
            RoadNetwork network,
            Road road,
            String segmentId) {
        if (segmentId == null || segmentId.isBlank()) {
            return Optional.empty();
        }
        for (OrientedRoadSegment segment : orientedSegments(network, road)) {
            if (segment.edgeId().equals(segmentId)) {
                return Optional.of(segment);
            }
        }
        return Optional.empty();
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

    /** @deprecated 使用 {@link #canonicalLength} */
    @Deprecated
    public static double totalLength(RoadNetwork network, Road road) {
        return canonicalLength(network, road);
    }

    /** @deprecated 使用 {@link #designLength} */
    @Deprecated
    public static double planLength(RoadNetwork network, Road road) {
        return designLength(network, road);
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
        if (network == null || road == null) {
            return false;
        }
        if (network.getRoad(road.getId()) == null) {
            return false;
        }
        for (RoadTopologyViolation violation : RoadTopologyInvariantValidator.validateRoad(network, road)) {
            if (violation.kind() == RoadTopologyViolationKind.ROAD_DISCONNECTED
                    || violation.kind() == RoadTopologyViolationKind.ROAD_BRANCHING) {
                return false;
            }
            if (violation.kind() == RoadTopologyViolationKind.ROAD_CYCLE
                    && road.getTopologyMode() != RoadTopologyMode.LOOP) {
                return false;
            }
        }
        return !orientedSegments(network, road).isEmpty();
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

    public static Optional<String> segmentChainEntryNodeId(RoadNetwork network, Road road, String segmentId) {
        return orientedSegment(network, road, segmentId).map(OrientedRoadSegment::entryNodeId);
    }

    public static Optional<String> segmentChainExitNodeId(RoadNetwork network, Road road, String segmentId) {
        return orientedSegment(network, road, segmentId).map(OrientedRoadSegment::exitNodeId);
    }

    public static double chainLocalFromGeometryLocal(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            double geometryLocalDistance) {
        return orientedSegment(network, road, edge.getId())
            .map(segment -> segment.chainLocalFromGeometryLocal(geometryLocalDistance))
            .orElse(Math.max(0.0, geometryLocalDistance));
    }

    public static double geometryLocalFromChainLocal(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            double chainLocalDistance) {
        return orientedSegment(network, road, edge.getId())
            .map(segment -> segment.geometryLocalFromChainLocal(chainLocalDistance))
            .orElse(Math.max(0.0, chainLocalDistance));
    }

    public static OptionalDouble nodeChainLocalDistance(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            String nodeId) {
        Optional<OrientedRoadSegment> oriented = orientedSegment(network, road, edge.getId());
        if (oriented.isEmpty() || nodeId == null || nodeId.isBlank()) {
            return OptionalDouble.empty();
        }
        OptionalDouble station = oriented.get().roadStationAtNode(nodeId);
        if (station.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(station.getAsDouble() - oriented.get().startStation());
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
     * 边内几何局部距离 → 道路桩号。
     */
    public static Optional<RoadStation> roadStationAtEdgeLocalDistance(
            RoadNetwork network,
            Road road,
            String segmentId,
            double geometryLocalDistance) {
        return stationAt(network, road, segmentId, geometryLocalDistance);
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
        Optional<OrientedRoadSegment> oriented = orientedSegment(network, road, segmentId);
        if (oriented.isEmpty()) {
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
        double instanceChainage = oriented.get().roadStationAtGeometryLocal(geometryLocalDistance);
        double chainage = toCanonicalChainage(network, road, instanceChainage);
        if (!isValid(network, new RoadStation(road.getId(), chainage))) {
            return Optional.empty();
        }
        return Optional.of(new RoadStation(road.getId(), chainage));
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

    public static String formatRange(double startMeters, double endMeters, RoadStationFormat format) {
        return format(startMeters, format) + " – " + format(endMeters, format);
    }

    public static List<Double> segmentStartStations(RoadNetwork network, Road road) {
        List<Double> stations = new ArrayList<>();
        for (OrientedRoadSegment segment : orientedSegments(network, road)) {
            stations.add(toCanonicalChainage(network, road, segment.startStation()));
        }
        return stations;
    }

    /**
     * Canonical 桩号 → 实例折线链上弧长（{@link OrientedRoadSegment} 域）。
     */
    static double toInstanceChainage(RoadNetwork network, Road road, double canonicalChainage) {
        double canonicalTotal = canonicalLength(network, road);
        double instanceTotal = instanceLength(network, road);
        if (!Double.isFinite(canonicalChainage) || canonicalTotal <= STATION_EPSILON) {
            return 0.0;
        }
        if (Math.abs(canonicalTotal - instanceTotal) <= STATION_EPSILON) {
            return canonicalChainage;
        }
        double ratio = Math.max(0.0, Math.min(1.0, canonicalChainage / canonicalTotal));
        return ratio * instanceTotal;
    }

    /**
     * 实例折线链上弧长 → canonical 桩号。
     */
    public static double toCanonicalChainage(RoadNetwork network, Road road, double instanceChainage) {
        double canonicalTotal = canonicalLength(network, road);
        double instanceTotal = instanceLength(network, road);
        if (!Double.isFinite(instanceChainage) || instanceTotal <= STATION_EPSILON) {
            return 0.0;
        }
        if (Math.abs(canonicalTotal - instanceTotal) <= STATION_EPSILON) {
            return instanceChainage;
        }
        double ratio = Math.max(0.0, Math.min(1.0, instanceChainage / instanceTotal));
        return ratio * canonicalTotal;
    }

    private record SegmentChainBinding(String segmentId, String entryNodeId) {
    }

    private static List<SegmentChainBinding> segmentChainBindings(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return List.of();
        }
        List<String> segmentIds = orderedSegments(network, road);
        if (segmentIds.isEmpty()) {
            return List.of();
        }

        Map<String, RoadEdge> edgesById = new HashMap<>();
        Map<String, List<String>> nodeToEdgeIds = new HashMap<>();
        for (String segmentId : segmentIds) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                continue;
            }
            edgesById.put(segmentId, edge);
            nodeToEdgeIds.computeIfAbsent(edge.getStartNodeId(), ignored -> new ArrayList<>()).add(segmentId);
            nodeToEdgeIds.computeIfAbsent(edge.getEndNodeId(), ignored -> new ArrayList<>()).add(segmentId);
        }
        if (edgesById.isEmpty()) {
            return List.of();
        }

        String startNodeId = findChainStart(network, nodeToEdgeIds);
        if (startNodeId == null) {
            return List.of();
        }

        List<SegmentChainBinding> bindings = new ArrayList<>(edgesById.size());
        Set<String> visited = new HashSet<>();
        String currentNodeId = startNodeId;
        String currentEdgeId = firstUnvisitedEdge(nodeToEdgeIds.get(startNodeId), visited);

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
            currentEdgeId = firstUnvisitedEdge(nodeToEdgeIds.get(nextNodeId), visited);
        }
        return bindings;
    }

    private static String findChainStart(RoadNetwork network, Map<String, List<String>> nodeToEdgeIds) {
        String endpoint = null;
        Vec2d endpointPos = null;
        for (Map.Entry<String, List<String>> entry : nodeToEdgeIds.entrySet()) {
            if (entry.getValue().size() != 1) {
                continue;
            }
            RoadNode node = network.getNode(entry.getKey());
            if (node == null || node.getPosition() == null) {
                continue;
            }
            Vec2d pos = node.getPosition();
            if (endpointPos == null || comparePosition(pos, endpointPos) < 0) {
                endpointPos = pos;
                endpoint = entry.getKey();
            }
        }
        if (endpoint != null) {
            return endpoint;
        }

        String fallback = null;
        Vec2d fallbackPos = null;
        for (String nodeId : nodeToEdgeIds.keySet()) {
            RoadNode node = network.getNode(nodeId);
            if (node == null || node.getPosition() == null) {
                continue;
            }
            Vec2d pos = node.getPosition();
            if (fallbackPos == null || comparePosition(pos, fallbackPos) < 0) {
                fallbackPos = pos;
                fallback = nodeId;
            }
        }
        return fallback;
    }

    private static int comparePosition(Vec2d left, Vec2d right) {
        int byX = Double.compare(left.x, right.x);
        return byX != 0 ? byX : Double.compare(left.y, right.y);
    }

    private static String firstUnvisitedEdge(List<String> edgeIds, Set<String> visited) {
        if (edgeIds == null) {
            return null;
        }
        for (String edgeId : edgeIds) {
            if (!visited.contains(edgeId)) {
                return edgeId;
            }
        }
        return null;
    }
}
