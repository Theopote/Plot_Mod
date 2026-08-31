package com.plot.plugin.road.station;

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
 * Road-local 里程（chainage）计算：沿有序分段链累计弧长。
 * <p>
 * 单位与 {@link RoadEdge#getLength()} 一致（canvas 平面距离，与纵断面 profile 里程对齐）。
 * 仅对拓扑可维护的道路（无分叉、无断开）保证语义；分叉/断开时 {@link #isStationable} 为 false。
 *
 * @see docs/development/task-assignments/RoadSystemPlugin_Phase2_Stationing_v1.md
 */
public final class RoadStationing {

    private static final double STATION_EPSILON = 1e-6;

    private RoadStationing() {
    }

    public static List<String> orderedSegments(RoadNetwork network, Road road) {
        return RoadSegmentOrdering.orderedSegmentIds(network, road);
    }

    public static double totalLength(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return 0.0;
        }
        double total = 0.0;
        for (String segmentId : orderedSegments(network, road)) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge != null) {
                total += edge.getLength();
            }
        }
        return total;
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
        return orderedSegments(network, road).size() > 0;
    }

    public static boolean isValid(RoadNetwork network, RoadStation station) {
        if (station == null) {
            return false;
        }
        Road road = network != null ? network.getRoad(station.roadId()) : null;
        if (!isStationable(network, road)) {
            return false;
        }
        double total = totalLength(network, road);
        return station.chainageMeters() >= -STATION_EPSILON
            && station.chainageMeters() <= total + STATION_EPSILON;
    }

    /**
     * 分段几何方向（start→end）是否与道路链方向一致。
     * <p>
     * 桩号沿拓扑链累计；边内 {@code localDistance} 仍以几何起点计量。
     * 当本方法为 false 时，需用 {@link #chainLocalFromGeometryLocal} 换算后再参与桩号查询。
     */
    public static boolean segmentFlowsWithGeometry(RoadNetwork network, Road road, String segmentId) {
        Optional<String> entryNodeId = segmentChainEntryNodeId(network, road, segmentId);
        if (entryNodeId.isEmpty()) {
            return true;
        }
        RoadEdge edge = network.getEdge(segmentId);
        if (edge == null) {
            return true;
        }
        return entryNodeId.get().equals(edge.getStartNodeId());
    }

    /**
     * 该分段在道路链上的入口节点（桩号沿链从此节点离开）。
     */
    public static Optional<String> segmentChainEntryNodeId(RoadNetwork network, Road road, String segmentId) {
        if (network == null || road == null || segmentId == null || segmentId.isBlank()) {
            return Optional.empty();
        }
        for (SegmentChainBinding binding : segmentChainBindings(network, road)) {
            if (binding.segmentId().equals(segmentId)) {
                return Optional.of(binding.entryNodeId());
            }
        }
        return Optional.empty();
    }

    /**
     * 边内几何局部距离（从 {@link RoadEdge#getStartNodeId()} 起）→ 链局部距离（从链入口起）。
     */
    public static double chainLocalFromGeometryLocal(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            double geometryLocalDistance) {
        if (edge == null || !Double.isFinite(geometryLocalDistance)) {
            return 0.0;
        }
        double clamped = Math.max(0.0, Math.min(geometryLocalDistance, edge.getLength()));
        if (segmentFlowsWithGeometry(network, road, edge.getId())) {
            return clamped;
        }
        return edge.getLength() - clamped;
    }

    /**
     * 链局部距离（从链入口起）→ 边内几何局部距离（从几何起点起）。
     */
    public static double geometryLocalFromChainLocal(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            double chainLocalDistance) {
        return chainLocalFromGeometryLocal(network, road, edge, chainLocalDistance);
    }

    /**
     * 节点在该分段链方向上的局部距离；非端点返回 empty。
     */
    public static OptionalDouble nodeChainLocalDistance(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            String nodeId) {
        if (edge == null || nodeId == null || nodeId.isBlank()) {
            return OptionalDouble.empty();
        }
        if (edge.getStartNodeId().equals(nodeId)) {
            return OptionalDouble.of(segmentFlowsWithGeometry(network, road, edge.getId()) ? 0.0 : edge.getLength());
        }
        if (edge.getEndNodeId().equals(nodeId)) {
            return OptionalDouble.of(segmentFlowsWithGeometry(network, road, edge.getId()) ? edge.getLength() : 0.0);
        }
        return OptionalDouble.empty();
    }

    public static double segmentStartStation(RoadNetwork network, Road road, String segmentId) {
        if (network == null || road == null || segmentId == null || segmentId.isBlank()) {
            return 0.0;
        }
        double station = 0.0;
        for (String orderedId : orderedSegments(network, road)) {
            if (orderedId.equals(segmentId)) {
                return station;
            }
            RoadEdge edge = network.getEdge(orderedId);
            if (edge != null) {
                station += edge.getLength();
            }
        }
        return -1.0;
    }

    public static Optional<RoadStation> stationAt(
            RoadNetwork network,
            Road road,
            String segmentId,
            double localDistance) {
        if (!isStationable(network, road) || segmentId == null || segmentId.isBlank()) {
            return Optional.empty();
        }
        if (!Double.isFinite(localDistance)) {
            return Optional.empty();
        }
        double segmentStart = segmentStartStation(network, road, segmentId);
        if (segmentStart < 0.0) {
            return Optional.empty();
        }
        RoadEdge edge = network.getEdge(segmentId);
        if (edge == null) {
            return Optional.empty();
        }
        if (localDistance < -STATION_EPSILON || localDistance > edge.getLength() + STATION_EPSILON) {
            return Optional.empty();
        }
        double chainLocal = chainLocalFromGeometryLocal(network, road, edge, localDistance);
        double chainage = segmentStart + chainLocal;
        if (!isValid(network, new RoadStation(road.getId(), chainage))) {
            return Optional.empty();
        }
        return Optional.of(new RoadStation(road.getId(), chainage));
    }

    public static Optional<SegmentStation> resolve(RoadNetwork network, Road road, double chainageMeters) {
        if (!isStationable(network, road) || !Double.isFinite(chainageMeters)) {
            return Optional.empty();
        }
        if (!isValid(network, new RoadStation(road.getId(), chainageMeters))) {
            return Optional.empty();
        }

        double remaining = chainageMeters;
        List<String> segments = orderedSegments(network, road);
        for (int i = 0; i < segments.size(); i++) {
            String segmentId = segments.get(i);
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                return Optional.empty();
            }
            double length = edge.getLength();
            boolean isLast = i == segments.size() - 1;
            if (remaining < length - STATION_EPSILON || isLast) {
                double chainLocal = Math.min(Math.max(0.0, remaining), length);
                double geometryLocal = geometryLocalFromChainLocal(network, road, edge, chainLocal);
                return Optional.of(new SegmentStation(segmentId, geometryLocal));
            }
            remaining -= length;
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
        if (network == null || road == null) {
            return stations;
        }
        double station = 0.0;
        for (String segmentId : orderedSegments(network, road)) {
            stations.add(station);
            RoadEdge edge = network.getEdge(segmentId);
            if (edge != null) {
                station += edge.getLength();
            }
        }
        return stations;
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
        com.plot.api.geometry.Vec2d endpointPos = null;
        for (Map.Entry<String, List<String>> entry : nodeToEdgeIds.entrySet()) {
            if (entry.getValue().size() != 1) {
                continue;
            }
            RoadNode node = network.getNode(entry.getKey());
            if (node == null || node.getPosition() == null) {
                continue;
            }
            com.plot.api.geometry.Vec2d pos = node.getPosition();
            if (endpointPos == null || comparePosition(pos, endpointPos) < 0) {
                endpointPos = pos;
                endpoint = entry.getKey();
            }
        }
        if (endpoint != null) {
            return endpoint;
        }

        String fallback = null;
        com.plot.api.geometry.Vec2d fallbackPos = null;
        for (String nodeId : nodeToEdgeIds.keySet()) {
            RoadNode node = network.getNode(nodeId);
            if (node == null || node.getPosition() == null) {
                continue;
            }
            com.plot.api.geometry.Vec2d pos = node.getPosition();
            if (fallbackPos == null || comparePosition(pos, fallbackPos) < 0) {
                fallbackPos = pos;
                fallback = nodeId;
            }
        }
        return fallback;
    }

    private static int comparePosition(com.plot.api.geometry.Vec2d left, com.plot.api.geometry.Vec2d right) {
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
