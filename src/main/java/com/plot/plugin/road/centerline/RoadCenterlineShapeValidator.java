package com.plot.plugin.road.centerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonValidator;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.RoadTopologyViolation;
import com.plot.plugin.road.model.RoadTopologyViolationKind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检测单条逻辑道路中心线的有害形状问题。
 * <p>
 * 路网级跨路求交会跳过同 {@code roadId} 边对；同路平面真交由
 * {@link com.plot.plugin.road.crossing.RoadStackCrossing} 注册（平交/立交），
 * <b>不</b>再报为形状违规——盘山路投影交叉、立交匝道、环路汇合后再引出出口等均为合法设计。
 * <p>
 * 仍报：共线折返重叠（桩号语义破坏）、内部分叉/未标记闭环（应拆多路或标 LOOP）。
 */
public final class RoadCenterlineShapeValidator {

    private RoadCenterlineShapeValidator() {
    }

    public static List<RoadCenterlineViolation> validate(RoadNetwork network) {
        if (network == null) {
            return List.of();
        }
        List<RoadCenterlineViolation> violations = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            violations.addAll(validateRoad(network, road));
        }
        return List.copyOf(violations);
    }

    public static List<RoadCenterlineViolation> validateRoad(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return List.of();
        }
        LinkedHashSet<RoadCenterlineViolationKind> kinds = new LinkedHashSet<>();
        String roadId = road.getId();

        if (hasNonLinearTopology(network, road)) {
            kinds.add(RoadCenterlineViolationKind.NON_LINEAR_ROAD_TOPOLOGY);
        }

        for (String segmentId : road.getOrderedSegmentIds()) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                continue;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            // 故意不报平面真交：见类注释 / RoadStackCrossing。
            if (PolygonValidator.hasOpenPolylineSelfOverlap(points)) {
                kinds.add(RoadCenterlineViolationKind.SELF_OVERLAP);
            }
        }

        List<Vec2d> chained = buildChainedCenterline(network, road);
        if (chained.size() >= 3
            && PolygonValidator.hasOpenPolylineSelfOverlap(chained)) {
            kinds.add(RoadCenterlineViolationKind.SELF_OVERLAP);
        }

        List<RoadCenterlineViolation> violations = new ArrayList<>(kinds.size());
        for (RoadCenterlineViolationKind kind : kinds) {
            violations.add(new RoadCenterlineViolation(roadId, kind));
        }
        return List.copyOf(violations);
    }

    public static Map<RoadCenterlineViolationKind, Integer> countByKind(RoadNetwork network) {
        EnumMap<RoadCenterlineViolationKind, Integer> counts =
            new EnumMap<>(RoadCenterlineViolationKind.class);
        for (RoadCenterlineViolationKind kind : RoadCenterlineViolationKind.values()) {
            counts.put(kind, 0);
        }
        for (RoadCenterlineViolation violation : validate(network)) {
            counts.merge(violation.kind(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    public static int countRoadsWithViolations(RoadNetwork network) {
        if (network == null) {
            return 0;
        }
        Set<String> roadIds = new HashSet<>();
        for (RoadCenterlineViolation violation : validate(network)) {
            roadIds.add(violation.roadId());
        }
        return roadIds.size();
    }

    private static boolean hasNonLinearTopology(RoadNetwork network, Road road) {
        for (RoadTopologyViolation violation : RoadTopologyInvariantValidator.validateRoad(network, road)) {
            if (violation.kind() == RoadTopologyViolationKind.ROAD_BRANCHING) {
                return true;
            }
            if (violation.kind() == RoadTopologyViolationKind.ROAD_CYCLE
                && road.getTopologyMode() != RoadTopologyMode.LOOP) {
                return true;
            }
        }
        return false;
    }

    public static List<Vec2d> buildChainedCenterline(RoadNetwork network, Road road) {
        List<String> segmentIds = RoadSegmentOrdering.orderedSegmentIds(network, road);
        if (segmentIds.isEmpty()) {
            return List.of();
        }
        if (segmentIds.size() == 1) {
            RoadEdge edge = network.getEdge(segmentIds.getFirst());
            return edge != null ? edge.getCenterlinePoints() : List.of();
        }

        Map<String, RoadEdge> edgesById = new HashMap<>();
        Map<String, List<String>> nodeToEdgeIds = new HashMap<>();
        for (String segmentId : segmentIds) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                continue;
            }
            edgesById.put(segmentId, edge);
            nodeToEdgeIds
                .computeIfAbsent(edge.getStartNodeId(), ignored -> new ArrayList<>())
                .add(segmentId);
            nodeToEdgeIds
                .computeIfAbsent(edge.getEndNodeId(), ignored -> new ArrayList<>())
                .add(segmentId);
        }
        if (edgesById.isEmpty()) {
            return List.of();
        }

        String startNodeId = findChainStart(nodeToEdgeIds);
        if (startNodeId == null) {
            return List.of();
        }

        List<Vec2d> merged = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentNodeId = startNodeId;
        String currentEdgeId = firstUnvisitedEdge(nodeToEdgeIds.get(startNodeId), visited);

        while (currentEdgeId != null) {
            visited.add(currentEdgeId);
            RoadEdge edge = edgesById.get(currentEdgeId);
            if (edge == null) {
                break;
            }
            List<Vec2d> oriented = orientCenterline(edge, currentNodeId);
            appendJoined(merged, oriented);
            String nextNodeId = edge.getStartNodeId().equals(currentNodeId)
                ? edge.getEndNodeId()
                : edge.getStartNodeId();
            currentNodeId = nextNodeId;
            currentEdgeId = firstUnvisitedEdge(nodeToEdgeIds.get(nextNodeId), visited);
        }
        return List.copyOf(merged);
    }

    private static String findChainStart(Map<String, List<String>> nodeToEdgeIds) {
        String fallback = null;
        for (Map.Entry<String, List<String>> entry : nodeToEdgeIds.entrySet()) {
            int degree = entry.getValue() != null ? entry.getValue().size() : 0;
            if (degree == 1) {
                return entry.getKey();
            }
            if (fallback == null) {
                fallback = entry.getKey();
            }
        }
        return fallback;
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

    private static List<Vec2d> orientCenterline(RoadEdge edge, String fromNodeId) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return List.of();
        }
        if (edge.getStartNodeId().equals(fromNodeId)) {
            return points;
        }
        if (edge.getEndNodeId().equals(fromNodeId)) {
            List<Vec2d> reversed = new ArrayList<>(points.size());
            for (int i = points.size() - 1; i >= 0; i--) {
                reversed.add(points.get(i).copy());
            }
            return reversed;
        }
        return List.of();
    }

    private static void appendJoined(List<Vec2d> target, List<Vec2d> segment) {
        if (segment.isEmpty()) {
            return;
        }
        int startIndex = 0;
        if (!target.isEmpty()
            && RoadGeometryUtils.pointsNear(
                target.getLast(),
                segment.getFirst(),
                RoadNetworkBuilder.NODE_TOLERANCE)) {
            startIndex = 1;
        }
        for (int i = startIndex; i < segment.size(); i++) {
            target.add(segment.get(i).copy());
        }
    }
}
